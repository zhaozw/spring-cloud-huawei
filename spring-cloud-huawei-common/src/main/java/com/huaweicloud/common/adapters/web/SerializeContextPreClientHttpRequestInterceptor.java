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

package com.huaweicloud.common.adapters.web;

import org.springframework.core.Ordered;
import org.springframework.http.HttpRequest;

import com.huaweicloud.common.context.InvocationContextHolder;

public class SerializeContextPreClientHttpRequestInterceptor implements PreClientHttpRequestInterceptor {
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void process(HttpRequest request, byte[] body) {
    request.getHeaders().add(InvocationContextHolder.SERIALIZE_KEY,
        InvocationContextHolder.serialize(InvocationContextHolder.getOrCreateInvocationContext()));
  }
}
