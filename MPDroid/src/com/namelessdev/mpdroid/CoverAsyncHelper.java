package com.namelessdev.mpdroid;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;

import org.pmix.cover.LastFMCover;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Download Covers Asynchronous with Messages
 * 
 * @author Stefan Agner
 * @version $Id: $
 */
public class CoverAsyncHelper extends Handler {
	private static final int EVENT_DOWNLOADCOVER = 0;
	private static final int EVENT_COVERDOWNLOADED = 1;
	private static final int EVENT_COVERNOTFOUND = 2;

	private String urlOverride = null;

	public String getUrlOverride() {
		return urlOverride;
	}

	public void setUrlOverride(String urlOverride) {
		this.urlOverride = urlOverride.replace(" ", "%20");
		Log.i("MPDroid", "Setting urlOverride : " + this.urlOverride);
	}

	public interface CoverDownloadListener {
		public void onCoverDownloaded(Bitmap cover);

		public void onCoverNotFound();
	}

	private Collection<CoverDownloadListener> coverDownloadListener;

	private CoverAsyncWorker oCoverAsyncWorker;

	public CoverAsyncHelper() {
		HandlerThread oThread = new HandlerThread("CoverAsyncWorker");
		oThread.start();
		oCoverAsyncWorker = new CoverAsyncWorker(oThread.getLooper());
		coverDownloadListener = new LinkedList<CoverDownloadListener>();
	}

	public void addCoverDownloadListener(CoverDownloadListener listener) {
		coverDownloadListener.add(listener);
	}

	public void downloadCover(String artist, String album) {
		CoverInfo info = new CoverInfo();
		info.sArtist = artist;
		info.sAlbum = album;
		oCoverAsyncWorker.obtainMessage(EVENT_DOWNLOADCOVER, info).sendToTarget();
	}

	public void handleMessage(Message msg) {
		switch (msg.what) {
		case EVENT_COVERDOWNLOADED:
			for (CoverDownloadListener listener : coverDownloadListener)
				listener.onCoverDownloaded((Bitmap) msg.obj);
			break;

		case EVENT_COVERNOTFOUND:
			for (CoverDownloadListener listener : coverDownloadListener)
				listener.onCoverNotFound();
			break;
		default:
			break;
		}
	}

	private class CoverAsyncWorker extends Handler {
		public CoverAsyncWorker(Looper looper) {
			super(looper);
		}

		public void handleMessage(Message msg) {
			switch (msg.what) {
			case EVENT_DOWNLOADCOVER:
				CoverInfo info = (CoverInfo) msg.obj;
				String url = null;
				try {
					// Get URL to the Cover...
					if (urlOverride == null) {
						url = LastFMCover.getCoverUrl(info.sArtist, info.sAlbum);
					} else {
						url = urlOverride;
					}
				} catch (Exception e1) {
					e1.printStackTrace();
					CoverAsyncHelper.this.obtainMessage(EVENT_COVERNOTFOUND).sendToTarget();
				}

				if (url == null) {
					CoverAsyncHelper.this.obtainMessage(EVENT_COVERNOTFOUND).sendToTarget();
					return;
				}

				Bitmap bmImg=download(url);
				
				if (null==bmImg && url.endsWith("/cover.jpg")) {
					bmImg=download(url.replace("/cover.jpg", "/cover.png"));
				}
				
				if (null==bmImg) {
					CoverAsyncHelper.this.obtainMessage(EVENT_COVERNOTFOUND).sendToTarget();
				} else {
					CoverAsyncHelper.this.obtainMessage(EVENT_COVERDOWNLOADED, bmImg).sendToTarget();
				}
				break;
			default:
			}
		}
	}

	private Bitmap download(String url) {
		URL myFileUrl = null;
		HttpURLConnection conn = null;
		try {
			// Download Cover File...
			myFileUrl = new URL(url);
			conn = (HttpURLConnection) myFileUrl.openConnection();
			conn.setDoInput(true);
			conn.setDoOutput(false);
			conn.connect();
			InputStream is = conn.getInputStream();
			Bitmap bmp=BitmapFactory.decodeStream(is);
			is.close();
			conn.disconnect();
			return bmp;
		} catch (MalformedURLException e) {
			return null;
		} catch (IOException e) {
			return null;
		} finally {
			if (null!=conn) {
				conn.disconnect();
			}
		}
		
	}

	private class CoverInfo {
		public String sArtist;
		public String sAlbum;
	}

}
