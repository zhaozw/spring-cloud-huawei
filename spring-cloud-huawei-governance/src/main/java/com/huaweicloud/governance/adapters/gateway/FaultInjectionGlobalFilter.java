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

package com.huaweicloud.governance.adapters.gateway;

import java.nio.charset.StandardCharsets;

import org.apache.servicecomb.governance.handler.FaultInjectionHandler;
import org.apache.servicecomb.governance.marker.GovernanceRequest;
import org.apache.servicecomb.http.client.common.HttpUtils;
import org.apache.servicecomb.injection.Fault;
import org.apache.servicecomb.injection.FaultInjectionDecorators;
import org.apache.servicecomb.injection.FaultInjectionDecorators.FaultInjectionDecorateCheckedSupplier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public class FaultInjectionGlobalFilter implements GlobalFilter, Ordered {
  private final FaultInjectionHandler faultInjectionHandler;

  private final Object faultObject = new Object();

  public FaultInjectionGlobalFilter(FaultInjectionHandler faultInjectionHandler) {
    this.faultInjectionHandler = faultInjectionHandler;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    GovernanceRequest governanceRequest = createGovernanceRequest(exchange);
    FaultInjectionDecorateCheckedSupplier<Object> ds =
        FaultInjectionDecorators.ofCheckedSupplier(() -> faultObject);
    Fault fault = faultInjectionHandler.getActuator(governanceRequest);
    if (fault != null) {
      ds.withFaultInjection(fault);
      try {
        Object result = ds.get();
        if (result != faultObject) {
          DataBuffer dataBuffer = exchange.getResponse().bufferFactory().allocateBuffer()
              .write(HttpUtils.serialize(result).getBytes(
                  StandardCharsets.UTF_8));
          return exchange.getResponse().writeWith(Mono.just(dataBuffer));
        }
      } catch (Throwable e) {
        return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "fault injected.", e));
      }
    }

    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private GovernanceRequest createGovernanceRequest(ServerWebExchange exchange) {
    GovernanceRequest request = new GovernanceRequest();
    request.setHeaders(exchange.getRequest().getHeaders().toSingleValueMap());
    request.setMethod(exchange.getRequest().getMethodValue());
    request.setUri(exchange.getRequest().getURI().getPath());
    return request;
  }
}
