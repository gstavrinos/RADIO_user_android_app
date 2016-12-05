/*
 * Copyright (C) 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android;

import com.google.common.base.Preconditions;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import org.ros.exception.RosRuntimeException;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public abstract class RosActivity extends Activity {

  private final ServiceConnection nodeMainExecutorServiceConnection;
  private final String notificationTicker;
  private final String notificationTitle;
  private final String masterUri;

  protected NodeMainExecutorService nodeMainExecutorService;

  private final class NodeMainExecutorServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
      nodeMainExecutorService = ((NodeMainExecutorService.LocalBinder) binder).getService();
      nodeMainExecutorService.addListener(new NodeMainExecutorServiceListener() {
        @Override
        public void onShutdown(NodeMainExecutorService nodeMainExecutorService) {
          if ( !isFinishing() ) {
            RosActivity.this.finish();
          }
        }
      });
      startNodeExecutor();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
  };

  protected RosActivity(String notificationTicker, String notificationTitle, String masterUri) {
    super();
    this.notificationTicker = notificationTicker;
    this.notificationTitle = notificationTitle;
    this.masterUri = masterUri;
    nodeMainExecutorServiceConnection = new NodeMainExecutorServiceConnection();
  }

  @Override
  protected void onStart() {
    super.onStart();
    startNodeMainExecutorService();
  }

  private void startNodeMainExecutorService() {
    Intent intent = new Intent(this, NodeMainExecutorService.class);
    intent.setAction(NodeMainExecutorService.ACTION_START);
    intent.putExtra(NodeMainExecutorService.EXTRA_NOTIFICATION_TICKER, notificationTicker);
    intent.putExtra(NodeMainExecutorService.EXTRA_NOTIFICATION_TITLE, notificationTitle);
    startService(intent);
    Preconditions.checkState(
        bindService(intent, nodeMainExecutorServiceConnection, BIND_AUTO_CREATE),
        "Failed to bind NodeMainExecutorService.");
  }

  @Override
  protected void onDestroy() {
    if (nodeMainExecutorService != null) {
      nodeMainExecutorService.shutdown();
      unbindService(nodeMainExecutorServiceConnection);
      // NOTE(damonkohler): The activity could still be restarted. In that case,
      // nodeMainExectuorService needs to be null for everything to be started
      // up again.
      nodeMainExecutorService = null;
    }
    super.onDestroy();
  }

  private void startNodeExecutor(){
    URI uri;
    try {
      uri = new URI(masterUri);
    }
    catch (URISyntaxException e) {
      throw new RosRuntimeException(e);
    }
    nodeMainExecutorService.setMasterUri(uri);
    // Run init() in a new thread as a convenience since it often requires
    // network access.
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        RosActivity.this.init(nodeMainExecutorService);
        return null;
      }
    }.execute();
  }

  public URI getMasterUri() {
    Preconditions.checkNotNull(nodeMainExecutorService);
    return nodeMainExecutorService.getMasterUri();
  }

  /**
   * This method is called in a background thread once this {@link android.app.Activity} has
   * been initialized with a master {@link java.net.URI} via the {@link MasterChooser}
   * and a {@link NodeMainExecutorService} has started. Your {@link NodeMain}s
   * should be started here using the provided {@link NodeMainExecutor}.
   * 
   * @param nodeMainExecutor
   *          the {@link NodeMainExecutor} created for this {@link android.app.Activity}
   */
  protected abstract void init(NodeMainExecutor nodeMainExecutor);

}