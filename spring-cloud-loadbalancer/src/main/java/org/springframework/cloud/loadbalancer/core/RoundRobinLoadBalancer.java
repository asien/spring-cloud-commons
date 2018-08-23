/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.loadbalancer.core;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.reactive.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.reactive.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.reactive.Request;
import org.springframework.cloud.client.loadbalancer.reactive.Response;
import org.springframework.cloud.client.loadbalancer.reactive.ServiceInstanceSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.env.Environment;

/**
 * @author Spencer Gibb
 */
public class RoundRobinLoadBalancer implements ReactorLoadBalancer<ServiceInstance> {

	private static final Log log = LogFactory.getLog(RoundRobinLoadBalancer.class);

	private final AtomicInteger position;
	private final LoadBalancerClientFactory clientFactory;
	private final String serviceId;

	public RoundRobinLoadBalancer(LoadBalancerClientFactory clientFactory,
								  Environment environment) {
		this(clientFactory, environment, new Random().nextInt(1000));
	}

	public RoundRobinLoadBalancer(LoadBalancerClientFactory clientFactory,
			Environment environment, int seedPosition) {
		this.serviceId = clientFactory.getName(environment);
		this.clientFactory = clientFactory;
		this.position = new AtomicInteger(seedPosition);
	}

	@Override
	/**
	 * see original https://github.com/Netflix/ocelli/blob/master/ocelli-core/src/main/java/netflix/ocelli/loadbalancer/RoundRobinLoadBalancer.java
	 */
	public Mono<Response<ServiceInstance>> choose(Request request) {
		// TODO: move supplier to Request?
		ServiceInstanceSupplier supplier = clientFactory.getInstance(this.serviceId,
				ServiceInstanceSupplier.class);
		return supplier.get().collectList().map(instances -> {
			if (instances.isEmpty()) {
				log.warn("No servers available for service: " + this.serviceId);
				return new EmptyResponse();
			}
			// TODO: enforce order?
			int pos = Math.abs(position.incrementAndGet());

			ServiceInstance instance = instances.get(pos % instances.size());

			return new DefaultResponse(instance);
		});
	}
}
