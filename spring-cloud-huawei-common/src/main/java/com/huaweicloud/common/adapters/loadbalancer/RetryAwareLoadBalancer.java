/*

 * Copyright (C) 2020-2022 Huawei Technologies Co., Ltd. All rights reserved.

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

package com.huaweicloud.common.adapters.loadbalancer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.RandomLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

import com.huaweicloud.common.configration.dynamic.LoadBalancerProperties;
import com.huaweicloud.common.context.InvocationContext;
import com.huaweicloud.common.context.InvocationContextHolder;

import reactor.core.publisher.Mono;

/**
 * load balancers to support retry on same and on next
 */
public class RetryAwareLoadBalancer implements ReactorServiceInstanceLoadBalancer {
  private final String serviceId;

  private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

  private final LoadBalancerProperties loadBalancerProperties;

  private final Map<String, ReactorServiceInstanceLoadBalancer> loadBalancers = new ConcurrentHashMap<>();

  public RetryAwareLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
      String serviceId, LoadBalancerProperties loadBalancerProperties) {
    this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    this.serviceId = serviceId;
    this.loadBalancerProperties = loadBalancerProperties;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Mono<Response<ServiceInstance>> choose(Request request) {
    InvocationContext context = InvocationContextHolder.getOrCreateInvocationContext();
    if (context.getLocalContext(RetryContext.RETRY_CONTEXT) == null) {
      // gateway do not use RetryContext
      ReactorServiceInstanceLoadBalancer loadBalancer = loadBalancers.computeIfAbsent(loadBalancerProperties.getRule(),
          key -> {
            if (LoadBalancerProperties.RULE_RANDOM.equals(key)) {
              return new RandomLoadBalancer(this.serviceInstanceListSupplierProvider, this.serviceId);
            } else {
              return new RoundRobinLoadBalancer(this.serviceInstanceListSupplierProvider, this.serviceId);
            }
          });
      return loadBalancer.choose(request);
    }

    // feign / restTemplate using RetryContext
    RetryContext retryContext = context.getLocalContext(RetryContext.RETRY_CONTEXT);
    if (retryContext.trySameServer() && retryContext.getLastServer() != null) {
      retryContext.incrementRetry();
      return Mono.just(new DefaultResponse(retryContext.getLastServer()));
    }

    ReactorServiceInstanceLoadBalancer loadBalancer = loadBalancers.computeIfAbsent(loadBalancerProperties.getRule(),
        key -> {
          if (LoadBalancerProperties.RULE_RANDOM.equals(key)) {
            return new RandomLoadBalancer(this.serviceInstanceListSupplierProvider, this.serviceId);
          } else {
            return new RoundRobinLoadBalancer(this.serviceInstanceListSupplierProvider, this.serviceId);
          }
        });
    return loadBalancer.choose(request).doOnSuccess(r -> retryContext.setLastServer(r.getServer()));
  }
}
