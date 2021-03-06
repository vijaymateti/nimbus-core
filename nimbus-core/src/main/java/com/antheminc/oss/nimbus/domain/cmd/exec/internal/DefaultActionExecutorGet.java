/**
 *  Copyright 2016-2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.antheminc.oss.nimbus.domain.cmd.exec.internal;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.antheminc.oss.nimbus.InvalidConfigException;
import com.antheminc.oss.nimbus.context.BeanResolverStrategy;
import com.antheminc.oss.nimbus.domain.cmd.Action;
import com.antheminc.oss.nimbus.domain.cmd.Command;
import com.antheminc.oss.nimbus.domain.cmd.CommandBuilder;
import com.antheminc.oss.nimbus.domain.cmd.CommandElement.Type;
import com.antheminc.oss.nimbus.domain.cmd.exec.AbstractFunctionCommandExecutor;
import com.antheminc.oss.nimbus.domain.cmd.exec.CommandExecution.Input;
import com.antheminc.oss.nimbus.domain.cmd.exec.CommandExecution.Output;
import com.antheminc.oss.nimbus.domain.cmd.exec.CommandExecutorGateway;
import com.antheminc.oss.nimbus.domain.cmd.exec.ExecutionContext;
import com.antheminc.oss.nimbus.domain.cmd.exec.FunctionHandler;
import com.antheminc.oss.nimbus.domain.config.builder.DomainConfigBuilder;
import com.antheminc.oss.nimbus.domain.defn.Repo;
import com.antheminc.oss.nimbus.domain.model.config.ModelConfig;
import com.antheminc.oss.nimbus.domain.model.state.EntityState.Param;
import com.antheminc.oss.nimbus.domain.model.state.InvalidStateException;
import com.antheminc.oss.nimbus.domain.model.state.QuadModel;
import com.antheminc.oss.nimbus.domain.model.state.internal.ExecutionEntity;
import com.antheminc.oss.nimbus.entity.process.ProcessFlow;

/**
 * @author Soham Chakravarti
 *
 */
public class DefaultActionExecutorGet extends AbstractFunctionCommandExecutor<Param<Object>, Object> {

	private CommandExecutorGateway commandGateway;
	
	private DomainConfigBuilder domainConfigBuilder;
	
	public DefaultActionExecutorGet(BeanResolverStrategy beanResolver) {
		super(beanResolver);
		
		this.commandGateway = getBeanResolver().find(CommandExecutorGateway.class);
		this.domainConfigBuilder = getBeanResolver().find(DomainConfigBuilder.class);
	}
	

	@SuppressWarnings("unchecked")
	@Override
	protected final Output<Object> executeInternal(Input input) {
		ExecutionContext eCtx = handleGetDomainRoot(input.getContext());
	
		Param<?> p = findParamByCommandOrThrowEx(eCtx);
		
		final Object outcome;
		if(containsFunctionHandler(input)) {
			outcome = executeFunctionHanlder(input, FunctionHandler.class);
			
		} else {
			outcome = p;
		}
		
		return Output.instantiate(input, eCtx, outcome);
	}
	
	protected ExecutionContext handleGetDomainRoot(ExecutionContext eCtx) {
		if(eCtx.getQuadModel()!=null)
			return eCtx;

		ModelConfig<?> rootDomainConfig = getRootDomainConfig(eCtx);
		QuadModel<?, ?> q =  createNewQuad(rootDomainConfig, eCtx);
		
		// set to context
		eCtx.setQuadModel(q);
		
		// hook-up BPM
		final ProcessFlow processEntityState = loadProcessState(rootDomainConfig, eCtx);
		q.getRoot().getState().setFlow(processEntityState);
		
		return eCtx;
	}
	
	protected QuadModel<?, ?> createNewQuad(ModelConfig<?> rootDomainConfig, ExecutionContext eCtx) {
		final String refId = eCtx.getCommandMessage().getCommand().getRefId(Type.DomainAlias);
		
		final Object entity;
		final Repo repo = rootDomainConfig.getRepo();
		final String resolvedRepAlias = resolveEntityAliasByRepo(rootDomainConfig);
		
		// db - entity
		if(Repo.Database.exists(repo) && StringUtils.isNotBlank(refId)) { // root (view or core) is persistent
			entity = getRepositoryFactory().get(rootDomainConfig.getRepo())
						._get(refId, rootDomainConfig.getReferredClass(), resolvedRepAlias, eCtx.getCommandMessage().getCommand().getAbsoluteUri());
			
		} else {
			entity = instantiateEntity(eCtx, rootDomainConfig);
		}

		if(rootDomainConfig.isMapped()) 
			return handleMapped(rootDomainConfig, eCtx, entity, Action._get);
		
		// create quad-model
		ExecutionEntity<?, ?> e = ExecutionEntity.resolveAndInstantiate(entity, null);
		
		return getQuadModelBuilder().build(eCtx.getCommandMessage().getCommand(), e);
	}

	protected QuadModel<?, ?> handleMapped(ModelConfig<?> rootDomainConfig, ExecutionContext eCtx, Object mapped, Action action) {
		ModelConfig<?> mapsToConfig = rootDomainConfig.findIfMapped().getMapsToConfig();

		// create mapsTo command and invoke command gateway to load coreParam
		Command mapsToCmd = CommandBuilder.from(eCtx.getCommandMessage().getCommand(), mapsToConfig.getAlias()).getCommand();
		mapsToCmd.setAction(action);
		
		Param<?> coreParam = Optional.ofNullable(commandGateway.execute(mapsToCmd, null))
								.map(mOut->(Param<?>)mOut.getSingleResult())
								.orElseThrow(()->new InvalidStateException("Expeceted first response from command gateway to return mapsTo core parm, but not found for mapsToCmd: "+mapsToCmd));
		
		return getQuadModelBuilder().build(eCtx.getCommandMessage().getCommand(), mapped, coreParam);
	}
	
	protected ProcessFlow loadProcessState(ModelConfig<?> rootDomainConfig, ExecutionContext eCtx) {
		String domainLifeCycle = rootDomainConfig.getDomainLifecycle();
		
		if(StringUtils.trimToNull(domainLifeCycle)==null)
			return null;
		
		ModelConfig<?> modelConfig = domainConfigBuilder.getModel(ProcessFlow.class);
		Repo repo = modelConfig.getRepo();
		
		if(!Repo.Database.exists(repo))
			throw new InvalidConfigException(ProcessFlow.class.getSimpleName()+" must have @Repo configured for db persistence, but found none.");
		
		String processStateAlias = StringUtils.isBlank(repo.alias()) ? modelConfig.getAlias() : repo.alias();
		
		final String resolvedEntityAlias = resolveEntityAliasByRepo(rootDomainConfig);
		String entityProcessAlias = resolvedEntityAlias + "_" + processStateAlias;
		
		final String entityRefId = eCtx.getCommandMessage().getCommand().getRefId(Type.DomainAlias);
		ProcessFlow processEntityState = getRepositoryFactory().get(repo)._get(entityRefId, ProcessFlow.class, entityProcessAlias);
		return processEntityState;
	}
}