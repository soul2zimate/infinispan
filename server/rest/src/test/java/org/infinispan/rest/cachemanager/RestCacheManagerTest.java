package org.infinispan.rest.cachemanager;

import static org.mockito.Mockito.never;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @since 9.2
 */
@Test(groups = "functional", testName = "rest.RestCacheManagerTest")
public class RestCacheManagerTest extends SingleCacheManagerTest {

   @BeforeClass
   public void prepare() {
      Configuration config = new ConfigurationBuilder().build();
      cacheManager.defineConfiguration("cache1", config);
      cacheManager.defineConfiguration("cache2", config);
   }

   @Test
   public void shouldReuseEncodedCaches() {
      EmbeddedCacheManager embeddedCacheManager = Mockito.spy(cacheManager);
      RestCacheManager<Object> restCacheManager = new RestCacheManager<>(embeddedCacheManager, c -> Boolean.FALSE);
      Map<String, Cache<String, ?>> knownCaches = TestingUtil.extractField(restCacheManager, "knownCaches");

      // Request cache by simple name
      restCacheManager.getCache("cache1");
      restCacheManager.getCache("cache2");

      // Verify they are stored internally
      assertEquals(knownCaches.size(), 2);

      // Requesting again should not cause interaction with the cache manager
      Mockito.reset(embeddedCacheManager);
      restCacheManager.getCache("cache1");
      restCacheManager.getCache("cache2");

      Mockito.verify(embeddedCacheManager, never()).getCache("cache1");
      Mockito.verify(embeddedCacheManager, never()).getCache("cache2");

      // Request cache with a certain mime type
      restCacheManager.getCache("cache2", MediaType.MATCH_ALL, MediaType.APPLICATION_JSON);
      restCacheManager.getCache("cache2", MediaType.MATCH_ALL, MediaType.TEXT_PLAIN);

      // Verify they are stored internally
      assertEquals(knownCaches.size(), 4);


      // Requesting again with same media, or with same media but different parameters should reuse internal instance
      Mockito.reset(embeddedCacheManager);
      restCacheManager.getCache("cache2", MediaType.MATCH_ALL, MediaType.TEXT_PLAIN);
      restCacheManager.getCache("cache2", MediaType.MATCH_ALL, MediaType.fromString("text/plain; charset=UTF-8"));
      restCacheManager.getCache("cache2", MediaType.MATCH_ALL, MediaType.fromString("text/plain; charset=SHIFT-JIS"));

      assertEquals(knownCaches.keySet().size(), 4);

      Mockito.verify(embeddedCacheManager, never()).getCache("cache1");
      Mockito.verify(embeddedCacheManager, never()).getCache("cache2");
   }

   @Override
   protected EmbeddedCacheManager createCacheManager() {
      return TestCacheManagerFactory.createCacheManager(getDefaultStandaloneCacheConfig(false));
   }
}
