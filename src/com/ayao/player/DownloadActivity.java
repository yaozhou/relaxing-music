package com.ayao.player ;

import android.app.DownloadManager ;
import android.app.DownloadManager.Request ;
import android.net.Uri ;
import android.content.Context ;
import android.content.IntentFilter ;
import android.content.BroadcastReceiver ;
import android.app.Activity ;
import android.os.Bundle ;
import android.content.Intent ;
import android.util.Log ;
import java.io.FileInputStream ;
import org.apache.http.util.EncodingUtils ;

import android.widget.Button ;
import android.view.View ;
import android.widget.TextView ;
import android.widget.LinearLayout ;
import android.widget.ScrollView ;

import java.util.HashMap ;
import java.io.File ;
import java.net.URLEncoder ;
import android.support.v4.content.LocalBroadcastManager ;

import java.net.DatagramSocket ;
import java.net.InetAddress ;
import java.net.DatagramPacket ;

import android.view.KeyEvent ;


public class DownloadActivity extends Activity implements Button.OnClickListener
{
    // index文件的下载地址
    private String INDEX_FILE_URL = "https://dl.dropboxusercontent.com/u/147517952/relax-music/index" ;
    // index文件的本地存储路径
    private String INDEX_FILE_PATH = "sdcard/music-app/index" ; 

    // 资源文件的url前缀（每个资源的URL地址=url前缀+资源文件名)
    private String m_UrlPrefix ;
    // 文件名，不是全路径名
    private String[] m_MusicFiles ; 

    private BroadcastReceiver m_BroadcastReceiver ;


    // 某个音效文件下载完成
    private void onDownloadCompleted(File f)
    {
	Log.v("DownloadActivity", "onDownloadCompleted(" + f.getName() + ")") ;

	if (f.getName().equals("index")) 
	    onIndexFileDownloadCompleted(f) ;
	else
	    onMusicFileDownloadCompleted(f) ;
	
	// disable对应的下载按钮
	// ...
    }

    private void onMusicFileDownloadCompleted(File f)
    {
	for(int i=0; i<m_MusicFiles.length; ++i) 
	    {
		Button btn = (Button)findViewById(i) ;
		if (m_MusicFiles[i].equals(f.getName()))
		    {
			btn.setText(m_MusicFiles[i] + "已下载") ;
			btn.setEnabled(false) ;
		    }
	    }
    }

    
    // index下载完成
    private void onIndexFileDownloadCompleted(File f)
    {
	if (!f.exists()) return ;

	// 读取文件到字符串中
	String indexStr = "" ;
	try {
	    FileInputStream fin = new FileInputStream(f.getAbsolutePath()) ;
	    int length = fin.available() ;
	    byte[] buffer = new byte[length] ;
	    fin.read(buffer) ;

	    indexStr = EncodingUtils.getString(buffer, "UTF-8") ;
	    fin.close() ;
	}catch (Exception e)
	    {
		e.printStackTrace() ;
	    }

	String[] lines = indexStr.split("\n") ;
	int num = lines.length - 1 ;

	// 解析音效文件url前缀，和文件名
	m_UrlPrefix = lines[0] ;
	m_MusicFiles = new String[num] ;
	for(int i=0; i<num; ++i){
	    m_MusicFiles[i] = lines[i+1] ;
	    Log.v("DownloadActivity", "MusicFile" + i + "(" + 
		  m_MusicFiles[i] + ")") ;
	}

	Log.v("DownloadActivity", "urlPrefix(" + m_UrlPrefix + ")") ;


	// 删除临时文件
	f.delete() ;

	initUI() ;
    }

    // 根据index文件中的音效文件，动态创建下载UI
    private void initUI()
    {
	Log.v("DownloadActivity", "initUI") ;

	LinearLayout llayout = new LinearLayout(this) ;
	llayout.setOrientation(LinearLayout.VERTICAL) ;
	
	for(int i=0; i<m_MusicFiles.length; ++i)
	    {
		if (alreadyHaveMusicFile(m_MusicFiles[i])) continue ;
		
		Button btn = new Button(this) ;
		btn.setId(i) ;
		btn.setText(m_MusicFiles[i]) ;
		llayout.addView(btn) ;
		btn.setOnClickListener(this) ;
	    }
	
	ScrollView sc = new ScrollView(this) ;
	sc.addView(llayout) ;
	setContentView(sc) ;

	setTitle("资源下载列表") ;
	int count = llayout.getChildCount() ;
	if (count == 0)
	    {
		TextView tv = new TextView(this) ;
		tv.setText("目前暂无新资源下载，如有喜欢的声音资源，可手动拷贝到/sdcard/music-app/目录，以使程序识别，也欢迎发送邮件给我(yaozhou.wuhu@gmail.com)，共享给更多用户。目前程序还有一些BUG，界面也还不够友好，敬请期待更多改进!") ;
		llayout.addView(tv) ;
	    }
    }

    boolean alreadyHaveMusicFile(String baseFileName)
    {
	File musicDir = new File("/sdcard/music-app/") ;
	File[] files = musicDir.listFiles() ;

	for(int i=0; i<files.length; ++i) {
	    if (files[i].getName().equals(baseFileName))
		return true ;
	}

	return false ;
    }

    @Override
	public void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState) ;

	File f = new File(INDEX_FILE_PATH) ;
	if (f.exists()) f.delete() ;
	
	startDownloadFile(INDEX_FILE_URL, "index", false) ;

	m_BroadcastReceiver = new BroadcastReceiver() {
		@Override
		    public void onReceive(Context context, Intent intent) {
		    String fileName = intent.getStringExtra("fileName") ;
		    File f = new File(fileName) ;
		    onDownloadCompleted(f) ;
		}
	    } ;

	LocalBroadcastManager.getInstance(this).registerReceiver(
	 m_BroadcastReceiver, new IntentFilter("download_completed"));

    }

    @Override
	public void onStop()
    {
	Log.v("DownloadActivity", "onStop") ;
	super.onStop() ;
	
	LocalBroadcastManager.getInstance(this).unregisterReceiver(
				   m_BroadcastReceiver) ;

    }

    @Override
	public void onClick(View view)
    {
	int idx = view.getId() ;
	
	try {
	    String url = m_UrlPrefix + URLEncoder.encode(
		 m_MusicFiles[idx], "UTF-8") ;

	    startDownloadFile(url, m_MusicFiles[idx], true) ;
	    ((Button)view).setText(m_MusicFiles[idx] + "(下载中)") ;
	    ((Button)view).setEnabled(false) ;
	}catch (Exception e) {
	    e.printStackTrace() ;
	}
    }

    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
    {
	switch (keyCode)
	    {
	    case KeyEvent.KEYCODE_VOLUME_UP:
		sendUDPPacket("up") ; return true ;
	    case KeyEvent.KEYCODE_VOLUME_DOWN:
		sendUDPPacket("down") ; return true ;
	    default:
		return super.onKeyUp(keyCode, event) ;
	    }
    }

    @Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
    {
	switch (keyCode)
	    {
	    case KeyEvent.KEYCODE_VOLUME_UP:
	    case KeyEvent.KEYCODE_VOLUME_DOWN:
		return true ;
	    default:
		return super.onKeyUp(keyCode, event) ;
	    }
    }
    
    private void sendUDPPacket(String s)
    {
	try {
	    DatagramSocket ds = new DatagramSocket() ;
	    
	    InetAddress addr = InetAddress.getByName("192.168.99.154") ;
	    DatagramPacket dp = new DatagramPacket(s.getBytes(),
				   s.length(), addr, 2121) ;

	    ds.send(dp) ;
	}catch (Exception e)
	    {
		e.printStackTrace() ;
	    }
    }

    private void startDownloadFile(String urlStr, String fileName, boolean bNotify)
    {
	Log.v("DownloadActivity", "startDownloadFile fileName(" + 
	      fileName + ") urlstr(" + urlStr + ")") ;

	Uri uri = Uri.parse(urlStr) ;
	DownloadManager.Request request = new Request(uri) ;
	request.setVisibleInDownloadsUi(bNotify) ;

	if (!bNotify) {
	    request.setNotificationVisibility(
	      DownloadManager.Request.VISIBILITY_HIDDEN) ;
	}

	request.setDestinationInExternalPublicDir("music-app", fileName) ;
	request.setAllowedNetworkTypes(Request.NETWORK_WIFI) ;

	DownloadManager dm = (DownloadManager)getSystemService(
			       Context.DOWNLOAD_SERVICE) ;

	dm.enqueue(request) ;
    }
}