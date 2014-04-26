package com.ayao.player ;

import android.app.Service ;
import android.content.Intent ;
import android.content.IntentFilter ;
import android.content.BroadcastReceiver ;
import android.app.DownloadManager ;
import android.app.DownloadManager.Request ;
import android.os.Binder ;
import android.os.IBinder ;
import android.util.Log ;
import android.app.ActivityManager ;
import android.content.Context ;

import android.media.MediaPlayer ;
import java.io.File ;
import android.app.Notification ;
import android.app.NotificationManager ;
import android.app.PendingIntent ;

import java.util.Timer ;
import java.util.TimerTask ;
import java.util.HashMap ;
import java.util.Map ;
import android.database.Cursor ;
import android.support.v4.content.LocalBroadcastManager ;



public class LocalMusicService extends Service 
{
    public static float DEFAULT_VOLUME = (float)1.0 ;
    public static String DEFAULT_MUSIC_PATH = "/sdcard/music-app/" ; 

    // 一个音效文件的播放实例
    public abstract class MusicTrack
    {
	protected File m_MusicFile ;
	protected boolean m_IsPlaying ;
	protected float m_Volume ;
	protected MediaPlayer m_MediaPlayer ;

	public abstract void setPlayingState(boolean bPlaying) ;

	public void setVolume(float v)
	{ 
	    m_Volume = v ; 
	    m_MediaPlayer.setVolume(m_Volume, m_Volume) ;
	}

	public File getMusicFile() { return m_MusicFile ; }

	public boolean getPlayingState() { return m_IsPlaying ; }

	public float getVolume() { return m_Volume ; }

	public void release() { m_MediaPlayer.release() ; }

    }

    // 循环音乐播放
    public class LoopMusicTrack extends MusicTrack
    {
	public LoopMusicTrack(String filePath)
	{
	    m_MusicFile = new File(filePath) ;
	    m_IsPlaying = false ;
	    m_Volume = (float)LocalMusicService.DEFAULT_VOLUME ;
	    
	    try {
		m_MediaPlayer = new MediaPlayer() ;
		m_MediaPlayer.setDataSource(filePath) ;
		m_MediaPlayer.prepare() ;
		m_MediaPlayer.setLooping(true) ;
		m_MediaPlayer.setVolume(m_Volume,m_Volume) ;
	    }catch (Exception e)
		{
		    e.printStackTrace() ;
		}
	}

	@Override
	    public void setPlayingState(boolean bPlaying)
	{
	    Log.v("LocalMusicService", "Loop setPlayingState(" + 
		  bPlaying + ") m_bPlaying(" + m_IsPlaying + ")") ;

	    if (m_IsPlaying == bPlaying) return ;
	    m_IsPlaying = bPlaying ;

	    if (bPlaying) m_MediaPlayer.start() ;
	    if (!bPlaying) m_MediaPlayer.pause() ;
	}
    }

    // 带延迟的音乐播放，用于“每隔两分钟播放一次火车经过的声音“的类似需求
    public class DelayMusicTrack extends MusicTrack implements MediaPlayer.OnCompletionListener
    {
	protected MusicTrackTimerTask m_TimerTask ;
	protected int m_Interval ; // 以秒为单位

	private class MusicTrackTimerTask extends TimerTask 
	{
	    @Override
		public void run()
	    {
		Log.v("LocalMusicService", "MusicTrackTimerTask Run") ;
		DelayMusicTrack.this.m_MediaPlayer.start() ;
	    }
	}

	@Override
	    public void onCompletion(MediaPlayer mp)
	{
	    Log.v("LocalMusicService", "Finished Playing") ;
	    	    
	}

	public DelayMusicTrack(String filePath, int interval)
	{
	    m_MusicFile = new File(filePath) ;
	    m_IsPlaying = false ;
	    m_Volume = (float)LocalMusicService.DEFAULT_VOLUME ;
	    
	    try {
		m_MediaPlayer = new MediaPlayer() ;
		m_MediaPlayer.setDataSource(filePath) ;
		m_MediaPlayer.prepare() ;
		m_MediaPlayer.setLooping(false) ;
		m_MediaPlayer.setOnCompletionListener(this) ;
		m_MediaPlayer.setVolume(m_Volume,m_Volume) ;
		int duration = m_MediaPlayer.getDuration() ;
		m_Interval = interval + duration ;
		Log.v("LocalMusicService", filePath + " duration(" + duration +
		      ") interval(" + interval + ")") ;
	    }catch (Exception e)
		{
		    e.printStackTrace() ;
		}
	}

	@Override
	    public void setPlayingState(boolean bPlaying)
	{
	    Log.v("LocalMusicService", "Delay setPlayingState(" + 
		  bPlaying + ") m_bPlaying(" + m_IsPlaying + ")") ;
	    
	    if (m_IsPlaying == bPlaying) return ;
	    m_IsPlaying = bPlaying ;



	    if (bPlaying)
		{
		    m_TimerTask = new MusicTrackTimerTask() ;
		    m_MediaPlayer.start() ;
		    LocalMusicService.this.m_Timer.scheduleAtFixedRate(
				       m_TimerTask, 0, m_Interval) ;
		}else
		{
		    m_TimerTask.cancel() ;
		    m_MediaPlayer.pause() ;
		}
	    
	}

    }

    public class MusicBinder extends Binder
    {
	public LocalMusicService getService()
	{
	    return LocalMusicService.this ;
	}
    }

    // 自动关闭定时器
    private class CountDownTask extends TimerTask
    {
	@Override
	    public void run()
	{
	    Log.v("MusicService", "TimerTask finished") ;
	    LocalMusicService.this.shutdown() ;
	}
    }

    // 音效资源列表
    public Map<String, MusicTrack>  m_MusicTrackMap ;

    // 自动关闭倒计时
    public Timer m_Timer ;
    private CountDownTask m_TimerTask ;
    int m_CurrentCountDown ; 	// in minutes
    
    MusicBinder m_MusicBinder ;

    public int getCurrentCountDown() { return m_CurrentCountDown ; }

    // 初始化通知栏
    private void initNotification()
    {
	Intent notificationIntent = new Intent(this, MainActivity.class);	

	PendingIntent pendingIntent = PendingIntent.getActivity(
		      this, 0, notificationIntent, 0);

	 Notification notification = new Notification(R.drawable.icon, 
		      "耀哥永远是我心目中的偶像", System.currentTimeMillis());

 	notification.setLatestEventInfo(
		      this, "睡吧,少年","宁静致远", pendingIntent); 	

	startForeground(1, notification) ;
    }

    // 初始化音效文件
    private void initMusicFiles()
    {	
	Log.v("LocalMusicService", "initMusicFiles") ;

	File musicPath = new File(DEFAULT_MUSIC_PATH) ;
	// 确保目录存在
	musicPath.mkdir() ;

	File[] files = musicPath.listFiles() ;

	for(int i=0; i<files.length; ++i)
	    {
		MusicTrack musicTrack = createMusicTrackFromFile(files[i]) ;
		String fileName = files[i].getName() ;
		
		// map中会已经存在这个值了么？
		m_MusicTrackMap.put(fileName, musicTrack) ;
		Log.v("LocalMusicService", "musicFiles(" + fileName + ")") ;
	    }
    }

    public MusicTrack createMusicTrackFromFile(File f)
    {
	MusicTrack musicTrack ;
	String fileName = f.getName() ;

	int idx1 = fileName.indexOf("!") ;
	int idx2 = fileName.indexOf(".") ;
	int interval = 0 ;
	if (idx1 != -1 && idx2 != -1) 
	    {
		String t = fileName.substring(idx1+1, idx2) ;
		try {
		    interval = Integer.parseInt(t) ;
		}catch(Exception e)
		    {

		    }
	    }

	if (interval == 0)
	    musicTrack = new LoopMusicTrack(f.getAbsolutePath()) ;
	else
	    musicTrack = new DelayMusicTrack(f.getAbsolutePath(), interval * 1000) ;

	Log.v("LocalMusicService", "create MusicTack(" + fileName + 
	      ") interval(" + interval + ")") ;

	return musicTrack ;

    }

    // 监听下载完成事件，只能在service中做，因为另外两个activity此时不一定在运行
    // 是否写道manifest.xml中更好 ?
    private void initDownloadCompleteReceiver()
    {
	IntentFilter filter = new IntentFilter(
	       DownloadManager.ACTION_DOWNLOAD_COMPLETE) ;

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		    public void onReceive(Context context, Intent intent)
		{
		    long id = intent.getLongExtra(
			  DownloadManager.EXTRA_DOWNLOAD_ID, -1) ;

		    LocalMusicService.this.onDownloadCompleted(id) ;
		}
	    } ;

	registerReceiver(receiver, filter) ;
    }

    // 监听下载完成事件，广播给MainActivity和DownloadActivity(如果在运行的话)
    private void onDownloadCompleted(long id)
    {
	Log.v("LocalMusicService", "onDownloadCompleted(" + id + ")") ;

	DownloadManager.Query query = new DownloadManager.Query() ;
	query.setFilterById(id) ;
	
	DownloadManager dm = (DownloadManager)getSystemService(
			       Context.DOWNLOAD_SERVICE) ;

	Cursor cursor = dm.query(query) ;
	
	if (cursor.moveToFirst())
	    {
		int status = cursor.getInt(
		   cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) ;
		
		if (status == DownloadManager.STATUS_SUCCESSFUL)
		    {
			int columnIdx = cursor.getColumnIndex(
			      DownloadManager.COLUMN_LOCAL_FILENAME) ;

			String fileName = cursor.getString(columnIdx) ;
			onDownloadCompleted(fileName) ;

		    }
	    }	
    }

    public void onDownloadCompleted(String fileName)
    {
	Log.v("LocalMusicService", "download completed(" + fileName + ")") ;

	boolean bIsMusicFile = onMusicFileDownloadCompleted(fileName) ;
	
	Intent intent = new Intent("download_completed") ;
        intent.putExtra("fileName", fileName) ;
	intent.putExtra("isMusic", bIsMusicFile) ;
	Log.v("localmusicservice", "isMusic(" + bIsMusicFile + ")") ;
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent) ;
	
	

    }

    public boolean onMusicFileDownloadCompleted(String fileName)
    {
	File f = new File(fileName) ;

	if (!f.getName().equals("index")) {
	    
	    MusicTrack musicTrack = createMusicTrackFromFile(f) ;
	    m_MusicTrackMap.put(f.getName(), musicTrack) ;	    
	    Log.v("LocalMusicService", "add musicFiles(" + f.getName()  + ")") ;
	    return true ;
	}
 
	return false ;
    }


    // 释放资源，关闭mediaPlayer
    private void shutdown()
    {
	for (String fileName: m_MusicTrackMap.keySet())  
		m_MusicTrackMap.get(fileName).release() ;

	stopSelf() ;
    }


    
    // 设置自动关闭定时器
    public void setShutdownTimer(int minutes)
    {
	Log.v("LocalMusicService", "setShutdownTimer(" + minutes + ")") ;
	m_CurrentCountDown = minutes ; // 用于activity启动时，显示当前计时
	
	if (m_TimerTask != null) m_TimerTask.cancel() ;
	m_TimerTask = new CountDownTask() ;

	// m_Timer.purge() ;	
	m_Timer.schedule(m_TimerTask, minutes * 60000) ;
    }

    // 设置某个音效的播放状态
    public void setPlayingState(String fileName, boolean bPlaying)
    {
	Log.v("LocalMusicService", "setPlayingState fileName(" + fileName + 
	      ") bPlaying(" + bPlaying + ")") ;

	MusicTrack mt = m_MusicTrackMap.get(fileName) ;
	mt.setPlayingState(bPlaying) ;
    }

    // 设置某个音效的音量
    public void setVolume(String fileName, float volume)
    {
	MusicTrack mt = m_MusicTrackMap.get(fileName) ;
	mt.setVolume(volume) ;
    }

    @Override
	public IBinder onBind(Intent intent)
    {
	return m_MusicBinder ;
    }

    @Override 
	public void onCreate()
    {
	Log.v("LocalMusicService", "onCreate") ;
	super.onCreate() ;

	m_MusicTrackMap = new HashMap<String, MusicTrack>() ;
	m_Timer = new Timer() ;

	m_CurrentCountDown = 0 ;

	m_MusicBinder = new MusicBinder() ;
	
	initMusicFiles() ;
	initNotification() ;
	initDownloadCompleteReceiver() ;
    }
}