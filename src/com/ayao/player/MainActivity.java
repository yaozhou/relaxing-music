package com.ayao.player;

import android.app.Activity;
import android.os.Bundle;
import android.media.MediaPlayer ;
import android.media.MediaPlayer.OnCompletionListener ;
import android.view.View.OnClickListener ;
import android.view.View ;
import android.view.ViewGroup ;
import android.view.Menu ;
import android.view.MenuInflater ;
import android.view.MenuItem ;

import android.widget.Button ;
import android.util.Log ;
import android.view.ViewGroup ;
import android.widget.LinearLayout ;
import android.widget.ScrollView ;
import android.widget.CheckBox ;
import android.widget.CompoundButton ;
import android.widget.ProgressBar ;
import android.widget.SeekBar ;

import java.io.File ;

import android.content.ComponentName ;
import android.content.Context ;
import android.content.Intent ;
import android.os.Bundle ;
import android.os.IBinder ;
import android.content.ServiceConnection ;

import android.app.Notification ;
import android.app.NotificationManager ;

import java.net.URL ;
import java.net.URLConnection ;
import java.net.HttpURLConnection ;
import java.io.BufferedInputStream ;
import java.io.BufferedOutputStream ;
import java.io.InputStream ;
import java.io.FileOutputStream ;
import android.os.StrictMode ;

import java.util.Map ;
import android.content.IntentFilter ;
import android.content.BroadcastReceiver ;
import android.util.AttributeSet ;
import android.support.v4.content.LocalBroadcastManager ;


public class MainActivity extends Activity implements 
           CheckBox.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener
{
    public class MusicServiceConnection implements ServiceConnection
    {
	@Override
	    public void onServiceDisconnected(ComponentName name)
	{
	    // 貌似还有问题，屏幕横转的时候，LOG提示有泄漏
	    MainActivity.this.finish() ;
	}

	@Override
	    public void onServiceConnected(ComponentName name, IBinder service)
	{
	    Log.v("MainActivity", "onServiceConnected name(" + name + ")") ;
	    LocalMusicService.MusicBinder binder = 
		(LocalMusicService.MusicBinder)service ;
	    
	    MainActivity.this.m_LocalMusicService = binder.getService() ;
	    MainActivity.this.initUI() ;
	}
    }

    public class MusicCheckBox extends CheckBox {
	public MusicCheckBox(Context context) { super(context) ; }
	public MusicCheckBox(Context context, AttributeSet attrs) { 
	    super(context, attrs) ;
	} ;
	public MusicCheckBox(Context context, AttributeSet attrs, int defStyle) {
	    super(context, attrs, defStyle) ;
	}
	public String m_MusicFileBaseName ;
    }

    public class MusicSeekBar extends SeekBar {
	public MusicSeekBar(Context context) { super(context) ; }
	public MusicSeekBar(Context context, AttributeSet attrs) {
	    super(context, attrs) ;
	}
	public MusicSeekBar(Context context, AttributeSet attrs, int defStyle) {
	    super(context, attrs, defStyle) ;
	}
	public String m_MusicFileBaseName ;
    }
				   

    public static int CHECKBOX_ID_START = 1000 ;
    public static int SEEKBAR_ID_START = 2000 ;

    public LocalMusicService m_LocalMusicService ;
    private BroadcastReceiver m_BroadcastReceiver  ;
    private LinearLayout m_ParentLayout ; 


    private void addAMusicTrack(String fileName, 
                                boolean isPlaying, float volume)
    {
	// 创建checkbox
	MusicCheckBox cb = new MusicCheckBox(this) ;
	cb.m_MusicFileBaseName = fileName ;
	cb.setId(View.generateViewId()) ;
	cb.setText(cb.m_MusicFileBaseName) ;
	cb.setChecked(isPlaying) ;
	m_ParentLayout.addView(cb) ;

		// 创建控制音量的seekBar
	MusicSeekBar sb = new MusicSeekBar(this, null,
		   android.R.attr.progressBarStyleHorizontal) ;
	sb.m_MusicFileBaseName = fileName ;
	sb.setId(View.generateViewId()) ;
	int v = (int)(volume * 100) ;
	sb.setProgress(v) ;
	m_ParentLayout.addView(sb) ;
		
	cb.setOnCheckedChangeListener(this) ;
	sb.setOnSeekBarChangeListener(this) ;	


    }

    // 根据service返回的当前信息，构建UI
    public void initUI()
    {
	Log.v("MyMusicPlayer", "Main Activity InitUI") ;

	// 当前的定时模式
	int countDown = m_LocalMusicService.getCurrentCountDown() ;
	if (countDown > 0)
	    setTitle("睡吧，少年(" + countDown + "分钟定时关闭)") ;

	Map<String, LocalMusicService.MusicTrack> musicTrackMap = 
	    m_LocalMusicService.m_MusicTrackMap ;

	Object[] mts = musicTrackMap.values().toArray() ;

	m_ParentLayout = new LinearLayout(this) ;
	m_ParentLayout.setOrientation(LinearLayout.VERTICAL) ;
	
	for(int i=0; i<mts.length; ++i)
	    {
		LocalMusicService.MusicTrack mt = 
		    (LocalMusicService.MusicTrack)mts[i] ;

		addAMusicTrack(mt.getMusicFile().getName() ,
			       mt.getPlayingState(), mt.getVolume()) ;
	    }

	// 让其支持上下滚动
	ScrollView sc = new ScrollView(this) ;
	sc.addView(m_ParentLayout) ;
	setContentView(sc) ;
    }


    @Override
	public boolean onCreateOptionsMenu(Menu menu)
    {
	MenuInflater inflater = getMenuInflater() ;
	inflater.inflate(R.menu.main_activity_actions, menu) ;
	return super.onCreateOptionsMenu(menu) ;
    }
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item)
    {
	int countDown = 0 ;

	switch (item.getItemId())
	    {
	    case R.id.auto_quit_15:
		countDown = 15 ; break ;
	    case R.id.auto_quit_30:
		countDown = 30 ; break ;
	    case R.id.auto_quit_45:
		countDown = 45 ; break ;
	    case R.id.auto_quit_60:
		countDown = 60 ; break ;
	    case R.id.auto_quit_90:
		countDown = 90 ; break ;
	    case R.id.auto_quit_120:
		countDown = 120 ; break ;
	    case R.id.quit:
		countDown = 0 ; break ;
	    case R.id.download:
		{
		    Intent intent = new Intent() ;
		    intent.setClass(this, DownloadActivity.class) ;
		    startActivity(intent) ;
		    return true ;
		}
	    default:
		return super.onOptionsItemSelected(item) ;
	    }
	m_LocalMusicService.setShutdownTimer(countDown) ;
	setTitle("睡吧,少年(" + countDown + "分钟定时关闭") ;
	return true ;
    }

    @Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
	String fileName = ((MusicSeekBar)seekBar).m_MusicFileBaseName ;

	m_LocalMusicService.setVolume(fileName,(float)progress / (float)100) ;
    }

    @Override
	public void onStartTrackingTouch(SeekBar seekBar)
    {
    }

    @Override
	public void onStopTrackingTouch(SeekBar seekBar)
    {
    }

    @Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
	String fileName = ((MusicCheckBox)buttonView).m_MusicFileBaseName ;	
	
	m_LocalMusicService.setPlayingState(fileName, isChecked) ;
    }


    public void onDownloadCompleted(File f)
    {
	Log.v("MainActivity", "onDownloadCompleted(" + f.getName() + ")") ;

	addAMusicTrack(f.getName(), false, (float)1.0) ;
    }

    @Override
	public void onCreate(Bundle savedInstanceState)
    {
	Log.v("MainActivity", "onCreate") ;
	super.onCreate(savedInstanceState) ;

	MusicServiceConnection sc = new MusicServiceConnection() ;
	Intent intent = new Intent(this, LocalMusicService.class) ;

	startService(intent) ;
 	boolean bRet = bindService(intent, sc, 0) ;

	Log.v("MainActivity", "bindService bRet(" + bRet + ")") ;

	// 监听下载完成事件，是否注册在manifest中更好？
	m_BroadcastReceiver = new BroadcastReceiver() {
		@Override
		    public void onReceive(Context context, Intent intent) {

		    String fileName = intent.getStringExtra("fileName") ;	
		    boolean isMusic = intent.getBooleanExtra("isMusic", false) ;
		    
		    if (isMusic) {
			File f = new File(fileName) ;
			onDownloadCompleted(f) ;
		    }
		}
	    } ;

	LocalBroadcastManager.getInstance(this).registerReceiver(
	 m_BroadcastReceiver, new IntentFilter("download_completed"));
	
	StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build() ;
	StrictMode.setThreadPolicy(policy) ;

    }
}