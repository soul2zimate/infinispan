package org.infinispan.server.hotrod

import org.testng.annotations.Test
import java.lang.reflect.Method
import test.HotRodTestingUtil._
import org.testng.Assert._
import org.infinispan.server.hotrod.test._
import org.infinispan.manager.EmbeddedCacheManager
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder
import javax.security.sasl.Sasl
import java.util.HashMap
import org.infinispan.server.core.security.simple.SimpleServerAuthenticationProvider
import org.jboss.sasl.JBossSaslProvider

/**
 * Hot Rod server authentication test.
 *
 * @author Tristan Tarrant
 * @since 7.0
 */
@Test(groups = Array("functional"), testName = "server.hotrod.HotRodAuthenticationTest")
class HotRodAuthenticationTest extends HotRodSingleNodeTest {
   val jbossSaslProvider = new JBossSaslProvider();

   override def createStartHotRodServer(cacheManager: EmbeddedCacheManager): HotRodServer = {
      val ssap = new SimpleServerAuthenticationProvider
      ssap.addUser("user", "realm", "password".toCharArray(), null)
      val builder = new HotRodServerConfigurationBuilder
      builder.authentication().enable().addAllowedMech("CRAM-MD5").serverAuthenticationProvider(ssap).serverName("localhost").addMechProperty(Sasl.POLICY_NOANONYMOUS, "true")
      startHotRodServer(cacheManager, UniquePortThreadLocal.get.intValue, 0, builder)
   }

   def testAuthMechList(m: Method) {
      val a = client.authMechList
      assertEquals(a.mechs.size, 1)
      assertTrue(a.mechs.contains("CRAM-MD5"))
      assertEquals(1, server.getDecoder.getTransport.acceptedChannels.size())
   }

   def testAuth(m: Method) {
      val props = new HashMap[String, String]
      val sc = Sasl.createSaslClient(Array("CRAM-MD5"), null, "hotrod", "localhost", props, new TestCallbackHandler("user", "realm", "password".toCharArray()))
      val res = client.auth(sc)
      assertTrue(res.complete)
      assertEquals(1, server.getDecoder.getTransport.acceptedChannels.size())
   }

   def testUnauthorizedOpCloseConnection(m: Method) {
      // Ensure the transport is clean
      server.getDecoder.getTransport.stop()
      server.getDecoder.getTransport.start()
      try {
        client.assertPutFail(m)
      } finally {
        assertEquals(0, server.getDecoder.getTransport.acceptedChannels.size())
      }
   }

}
