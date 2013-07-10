package org.infinispan.loaders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.util.ReflectionUtil;
import org.infinispan.configuration.cache.CacheStoreConfiguration;
import org.infinispan.configuration.cache.CacheStoreConfigurationBuilder;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.loaders.dummy.DummyInMemoryCacheStoreConfigurationBuilder;
import org.infinispan.loaders.spi.AbstractCacheStore;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.util.DefaultTimeService;
import org.infinispan.util.concurrent.WithinThreadExecutor;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests that cover {@link  AbstractCacheStoreTest }
 *
 * @author Adrian Cole
 * @since 4.0
 */
@Test(groups = "unit", testName = "loaders.AbstractCacheStoreTest")
public class AbstractCacheStoreTest extends AbstractInfinispanTest {
   private AbstractCacheStore cs;

   @BeforeMethod
   public void setUp() throws NoSuchMethodException, CacheLoaderException {
      cs = mock(AbstractCacheStore.class, Mockito.CALLS_REAL_METHODS);
   }

   @AfterMethod
   public void tearDown() throws CacheLoaderException {
      cs.stop();
      cs = null;
   }

   @Test
   void testSyncExecutorIsSetWhenCfgPurgeSyncIsTrueOnStart() throws Exception {
      CacheStoreConfiguration storeConfiguration = TestCacheManagerFactory.getDefaultCacheConfiguration(false)
            .loaders()
            .addLoader(DummyInMemoryCacheStoreConfigurationBuilder.class)
               .purgeSynchronously(true)
            .create();
      cs.init(storeConfiguration, mockCache(getClass().getName()), null);
      cs.start();
      ExecutorService service = (ExecutorService) ReflectionUtil.getValue(cs, "purgerService");
      assert service instanceof WithinThreadExecutor;
   }

   @Test
   void testAsyncExecutorIsDefaultOnStart() throws Exception {
      CacheStoreConfiguration storeConfiguration = TestCacheManagerFactory.getDefaultCacheConfiguration(false)
            .loaders()
            .addLoader(DummyInMemoryCacheStoreConfigurationBuilder.class)
            .create();
      cs.init(storeConfiguration, mockCache(getClass().getName()), null);
      cs.start();
      ExecutorService service = (ExecutorService) ReflectionUtil.getValue(cs, "purgerService");
      assert !(service instanceof WithinThreadExecutor);
   }

   public static Cache mockCache(final String name) {
      AdvancedCache cache = mock(AdvancedCache.class);
      ComponentRegistry registry = mock(ComponentRegistry.class);

      when(cache.getName()).thenReturn(name);
      when(cache.getAdvancedCache()).thenReturn(cache);
      when(cache.getComponentRegistry()).thenReturn(registry);
      when(registry.getTimeService()).thenReturn(TIME_SERVICE);
      when(cache.getStatus()).thenReturn(ComponentStatus.RUNNING);
      return cache;
   }
}
