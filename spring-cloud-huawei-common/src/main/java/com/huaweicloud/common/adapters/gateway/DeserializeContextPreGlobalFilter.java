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

package com.huaweicloud.common.adapters.gateway;

import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import com.huaweicloud.common.configration.dynamic.ContextProperties;
import com.huaweicloud.common.context.InvocationContext;
import com.huaweicloud.common.context.InvocationContextHolder;

public class DeserializeContextPreGlobalFilter implements PreGlobalFilter {
  private final ContextProperties contextProperties;

  public DeserializeContextPreGlobalFilter(
      ContextProperties contextProperties) {
    this.contextProperties = contextProperties;
  }

  @Override
  public void process(ServerWebExchange exchange) {
    InvocationContext context = InvocationContextHolder.deserializeAndCreate(
        exchange.getRequest().getHeaders().getFirst(InvocationContextHolder.SERIALIZE_KEY));

    contextProperties.getHeaderContextMapper()
        .forEach((k, v) -> context.putContext(v, exchange.getRequest().getHeaders().getFirst(k)));
    contextProperties.getQueryContextMapper()
        .forEach((k, v) -> context.putContext(v, exchange.getRequest().getQueryParams().getFirst(k)));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
