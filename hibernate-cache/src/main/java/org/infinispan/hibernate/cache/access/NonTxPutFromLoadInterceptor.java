/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.infinispan.hibernate.cache.access;

import org.infinispan.hibernate.cache.util.BeginInvalidationCommand;
import org.infinispan.hibernate.cache.util.CacheCommandInitializer;
import org.infinispan.hibernate.cache.util.EndInvalidationCommand;

import org.infinispan.hibernate.cache.util.InfinispanMessageLogger;
import org.infinispan.commands.write.InvalidateCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.interceptors.BaseCustomAsyncInterceptor;
import org.infinispan.remoting.inboundhandler.DeliverOrder;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.rpc.RpcOptions;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.StateTransferManager;
import org.infinispan.util.ByteString;

import java.util.List;

/**
 * Non-transactional counterpart of {@link TxPutFromLoadInterceptor}.
 * Invokes {@link PutFromLoadValidator#beginInvalidatingKey(Object, Object)} for each invalidation from
 * remote node ({@link BeginInvalidationCommand} and sends {@link EndInvalidationCommand} after the transaction
 * is complete, with help of {@link InvalidationSynchronization};
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NonTxPutFromLoadInterceptor extends BaseCustomAsyncInterceptor {
	private final static InfinispanMessageLogger log = InfinispanMessageLogger.Provider.getLog(NonTxPutFromLoadInterceptor.class);
	private final ByteString cacheName;
	private final PutFromLoadValidator putFromLoadValidator;
	private CacheCommandInitializer commandInitializer;
	private RpcManager rpcManager;
	private StateTransferManager stateTransferManager;
	private RpcOptions asyncUnordered;

	public NonTxPutFromLoadInterceptor(PutFromLoadValidator putFromLoadValidator, ByteString cacheName) {
		this.putFromLoadValidator = putFromLoadValidator;
		this.cacheName = cacheName;
	}

	@Inject
	public void injectDependencies(CacheCommandInitializer commandInitializer, RpcManager rpcManager, StateTransferManager stateTransferManager) {
		this.commandInitializer = commandInitializer;
		this.rpcManager = rpcManager;
		this.stateTransferManager = stateTransferManager;
	}

	@Start
	public void start() {
		asyncUnordered = rpcManager.getRpcOptionsBuilder(ResponseMode.ASYNCHRONOUS, DeliverOrder.NONE).build();
	}

	@Override
	public Object visitInvalidateCommand(InvocationContext ctx, InvalidateCommand command) throws Throwable {
		if (!ctx.isOriginLocal() && command instanceof BeginInvalidationCommand) {
			for (Object key : command.getKeys()) {
				putFromLoadValidator.beginInvalidatingKey(((BeginInvalidationCommand) command).getLockOwner(), key);
			}
		}
		return invokeNext(ctx, command);
	}

	public void endInvalidating(Object key, Object lockOwner, boolean successful) {
		assert lockOwner != null;
		if (!putFromLoadValidator.endInvalidatingKey(lockOwner, key, successful)) {
			log.failedEndInvalidating(key, cacheName);
		}

		EndInvalidationCommand endInvalidationCommand = commandInitializer.buildEndInvalidationCommand(
				cacheName, new Object[] { key }, lockOwner);
		List<Address> members = stateTransferManager.getCacheTopology().getMembers();
		rpcManager.invokeRemotely(members, endInvalidationCommand, asyncUnordered);
	}
}
