/*
 * Copyright 2020 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.xds.internal.certprovider;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.Status;
import io.grpc.xds.internal.sds.Closeable;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A plug-in that provides certificates required by the xDS security component and created
 * using the certificate-provider config from the xDS server.
 *
 * <p>We may move this out of the internal package and make this an official API in the future.
 *
 * <p>The plugin fetches certificates - root and optionally identity cert - required by xDS
 * security.
 */
public abstract class CertificateProvider implements Closeable {

  /** A watcher is registered to receive certificate updates. */
  public interface Watcher {
    void updateCertificate(PrivateKey key, List<X509Certificate> certChain);

    void updateTrustedRoots(List<X509Certificate> trustedRoots);

    void onError(Status errorStatus);
  }

  @VisibleForTesting
  static final class DistributorWatcher implements Watcher {
    @VisibleForTesting
    final Set<Watcher> downsstreamWatchers = new HashSet<>();

    synchronized void addWatcher(Watcher watcher) {
      downsstreamWatchers.add(watcher);
    }

    synchronized void removeWatcher(Watcher watcher) {
      downsstreamWatchers.remove(watcher);
    }

    @Override
    public void updateCertificate(PrivateKey key, List<X509Certificate> certChain) {
      for (Watcher watcher : downsstreamWatchers) {
        watcher.updateCertificate(key, certChain);
      }
    }

    @Override
    public void updateTrustedRoots(List<X509Certificate> trustedRoots) {
      for (Watcher watcher : downsstreamWatchers) {
        watcher.updateTrustedRoots(trustedRoots);
      }
    }

    @Override
    public void onError(Status errorStatus) {
      for (Watcher watcher : downsstreamWatchers) {
        watcher.onError(errorStatus);
      }
    }
  }

  /**
   * Concrete subclasses will call this to register the {@link Watcher}.
   *
   * @param watcher to register
   * @param notifyCertUpdates if true, the provider is required to call the watcher’s
   *     updateCertificate method. Implies the Provider is capable of minting certificates.
   *     Used by server-side and mTLS client-side. Note the Provider is always required
   *     to call updateTrustedRoots to provide trusted-root updates.
   */
  protected CertificateProvider(DistributorWatcher watcher, boolean notifyCertUpdates) {
    this.watcher = watcher;
    this.notifyCertUpdates = notifyCertUpdates;
  }

  /** Releases all resources and stop cert refreshes and watcher updates. */
  @Override
  public abstract void close();

  private final DistributorWatcher watcher;
  private final boolean notifyCertUpdates;

  public DistributorWatcher getWatcher() {
    return watcher;
  }

  public boolean isNotifyCertUpdates() {
    return notifyCertUpdates;
  }


}