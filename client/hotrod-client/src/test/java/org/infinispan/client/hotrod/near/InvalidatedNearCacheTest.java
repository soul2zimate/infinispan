package org.infinispan.client.hotrod.near;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.withRemoteCacheManager;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.client.hotrod.impl.InvalidatedNearRemoteCache;
import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.client.hotrod.test.RemoteCacheManagerCallable;
import org.infinispan.client.hotrod.test.SingleHotRodServerTest;
import org.infinispan.commons.CacheConfigurationException;
import org.testng.annotations.Test;

@Test(groups = {"functional", "smoke"}, testName = "client.hotrod.near.InvalidatedNearCacheTest")
public class InvalidatedNearCacheTest extends SingleHotRodServerTest {

   AssertsNearCache<Integer, String> assertClient;

   @Override
   protected RemoteCacheManager getRemoteCacheManager() {
      assertClient = createAssertClient();
      return assertClient.manager;
   }

   protected <K, V> AssertsNearCache<K, V> createAssertClient() {
      ConfigurationBuilder builder = clientConfiguration();
      return AssertsNearCache.create(this.cache(), builder);
   }

   protected <K, V> RemoteCache<K, V> createClient() {
      ConfigurationBuilder builder = clientConfiguration();
      return new RemoteCacheManager(builder.build()).getCache();
   }

   private ConfigurationBuilder clientConfiguration() {
      ConfigurationBuilder builder = HotRodClientTestingUtil.newRemoteConfigurationBuilder();
      builder.addServer().host("127.0.0.1").port(hotrodServer.getPort());
      builder.nearCache().mode(getNearCacheMode()).maxEntries(-1);
      return builder;
   }

   protected NearCacheMode getNearCacheMode() {
      return NearCacheMode.INVALIDATED;
   }

   public void testGetNearCacheAfterConnect() {
      ConfigurationBuilder builder = HotRodClientTestingUtil.newRemoteConfigurationBuilder(hotrodServer);
      builder.nearCache().mode(getNearCacheMode()).maxEntries(-1);
      RemoteCacheManager manager = new RemoteCacheManager(builder.build());
      try {
         RemoteCache cache = manager.getCache();
         cache.put(1, "one");
         cache.put(2, "two");
      } finally {
         HotRodClientTestingUtil.killRemoteCacheManager(manager);
      }

      AssertsNearCache<Integer, String> newAssertClient = AssertsNearCache.create(cache(), builder);
      try {
         assertEquals(2, newAssertClient.remote.size());
         newAssertClient.expectNoNearEvents();
         newAssertClient.get(1, "one").expectNearGetValue(1, null).expectNearPutIfAbsent(1, "one");
         newAssertClient.get(2, "two").expectNearGetValue(2, null).expectNearPutIfAbsent(2, "two");
         newAssertClient.remove(1).expectNearRemove(1);
         newAssertClient.remove(2).expectNearRemove(2);
      } finally {
         newAssertClient.stop();
      }
      assertClient.resetEvents();
   }

   public void testGetNearCache() {
      assertClient.expectNoNearEvents();
      assertClient.get(1, null).expectNearGetNull(1);
      assertClient.put(1, "v1").expectNearPreemptiveRemove(1);
      assertClient.get(1, "v1").expectNearGetNull(1).expectNearPutIfAbsent(1, "v1");
      assertClient.get(1, "v1").expectNearGetValue(1, "v1");
      assertClient.remove(1).expectNearRemove(1);
      assertClient.get(1, null).expectNearGetNull(1);
   }

   public void testGetAsyncNearCache() throws ExecutionException, InterruptedException {
      assertClient.expectNoNearEvents();
      assertClient.getAsync(1, null).expectNearGetNull(1);
      assertClient.putAsync(1, "v1").expectNearPreemptiveRemove(1);
      assertClient.getAsync(1, "v1").expectNearGetNull(1).expectNearPutIfAbsent(1, "v1");
      assertClient.getAsync(1, "v1").expectNearGetValue(1, "v1");
      assertClient.removeAsync(1).expectNearRemove(1);
      assertClient.getAsync(1, null).expectNearGetNull(1);
   }

   public void testGetVersionedNearCache() {
      assertClient.expectNoNearEvents();
      assertClient.getVersioned(1, null).expectNearGetNull(1);
      assertClient.put(1, "v1").expectNearPreemptiveRemove(1);
      assertClient.getVersioned(1, "v1").expectNearGetNull(1).expectNearPutIfAbsent(1, "v1");
      assertClient.getVersioned(1, "v1").expectNearGetValueVersion(1, "v1");
      assertClient.remove(1).expectNearRemove(1);
      assertClient.getVersioned(1, null).expectNearGetNull(1);
   }

   public void testGetWithMetadataNearCache() {
      assertClient.expectNoNearEvents();
      assertClient.getWithMetadata(1, null).expectNearGetNull(1);
      assertClient.put(1, "v1").expectNearPreemptiveRemove(1);
      assertClient.getWithMetadata(1, "v1").expectNearGetNull(1).expectNearPutIfAbsent(1, "v1");
      assertClient.getWithMetadata(1, "v1").expectNearGetValueVersion(1, "v1");
      assertClient.remove(1).expectNearRemove(1);
      assertClient.getWithMetadata(1, null).expectNearGetNull(1);
   }

   public void testGetWithMetadataAsyncNearCache() throws ExecutionException, InterruptedException {
      assertClient.expectNoNearEvents();
      assertClient.getWithMetadataAsync(1, null).expectNearGetNull(1);
      assertClient.putAsync(1, "v1").expectNearPreemptiveRemove(1);
      assertClient.getWithMetadataAsync(1, "v1").expectNearGetNull(1).expectNearPutIfAbsent(1, "v1");
      assertClient.getWithMetadataAsync(1, "v1").expectNearGetValueVersion(1, "v1");
      assertClient.removeAsync(1).expectNearRemove(1);
      assertClient.getWithMetadataAsync(1, null).expectNearGetNull(1);
   }

   public void testUpdateNearCache() {
      assertClient.expectNoNearEvents();
      assertClient.put(1, "v1").expectNearPreemptiveRemove(1);
      assertClient.put(1, "v2").expectNearRemove(1);
      assertClient.get(1, "v2").expectNearGetNull(1).expectNearPutIfAbsent(1, "v2");
      assertClient.get(1, "v2").expectNearGetValue(1, "v2");
      assertClient.put(1, "v3").expectNearRemove(1);
      assertClient.remove(1).expectNearRemove(1);
   }

   public void testUpdateAsyncNearCache() throws ExecutionException, InterruptedException {
      assertClient.expectNoNearEvents();
      assertClient.putAsync(1, "v1").expectNearPreemptiveRemove(1);
      assertClient.putAsync(1, "v2").expectNearRemove(1);
      assertClient.getAsync(1, "v2").expectNearGetNull(1).expectNearPutIfAbsent(1, "v2");
      assertClient.getAsync(1, "v2").expectNearGetValue(1, "v2");
      assertClient.putAsync(1, "v3").expectNearRemove(1);
      assertClient.removeAsync(1).expectNearRemove(1);
      assertClient.putAsync(1, "v4", 3, TimeUnit.SECONDS).expectNearPreemptiveRemove(1);
      assertClient.putAsync(1, "v5", 3, TimeUnit.SECONDS, 3, TimeUnit.SECONDS).expectNearRemove(1);
   }

   public void testGetUpdatesNearCache() {
      assertClient.expectNoNearEvents();
      assertClient.put(1, "v1").expectNearPreemptiveRemove(1);

      final AssertsNearCache<Integer, String> newAsserts = createAssertClient();
      withRemoteCacheManager(new RemoteCacheManagerCallable(newAsserts.manager) {
         @Override
         public void call() {
            newAsserts.expectNoNearEvents();
            newAsserts.get(1, "v1").expectNearGetNull(1).expectNearPutIfAbsent(1, "v1");
         }
      });
   }

   public void testGetAsyncUpdatesNearCache() throws ExecutionException, InterruptedException {
      assertClient.expectNoNearEvents();
      assertClient.putAsync(1, "v1").expectNearPreemptiveRemove(1);

      final AssertsNearCache<Integer, String> newAsserts = createAssertClient();
      withRemoteCacheManager(new RemoteCacheManagerCallable(newAsserts.manager) {
         @Override
         public void call() {
            newAsserts.expectNoNearEvents();
            try {
               newAsserts.getAsync(1, "v1").expectNearGetNull(1).expectNearPutIfAbsent(1, "v1");
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }
      });
   }

   @Test(expectedExceptions = CacheConfigurationException.class,
         expectedExceptionsMessageRegExp = ".*When enabling near caching, number of max entries must be configured.*")
   public void testConfigurationWithoutMaxEntries() {
      ConfigurationBuilder builder = HotRodClientTestingUtil.newRemoteConfigurationBuilder(hotrodServer);
      builder.nearCache().mode(getNearCacheMode());
      new RemoteCacheManager(builder.build());
   }

   public void testNearCacheNamePattern() {
      cacheManager.defineConfiguration("nearcache", new org.infinispan.configuration.cache.ConfigurationBuilder().build());
      ConfigurationBuilder builder = clientConfiguration();
      builder.nearCache().cacheNamePattern("near.*");
      RemoteCacheManager manager = new RemoteCacheManager(builder.build());
      try {
         RemoteCache nearcache = manager.getCache("nearcache");
         assertTrue(nearcache instanceof InvalidatedNearRemoteCache);
         RemoteCache cache = manager.getCache();
         assertFalse(cache instanceof InvalidatedNearRemoteCache);
      } finally {
         HotRodClientTestingUtil.killRemoteCacheManager(manager);
      }
   }

}
