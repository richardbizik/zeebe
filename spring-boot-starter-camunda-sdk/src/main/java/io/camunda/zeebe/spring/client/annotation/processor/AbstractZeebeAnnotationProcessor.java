/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
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
package io.camunda.zeebe.spring.client.annotation.processor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import org.springframework.beans.factory.BeanNameAware;

public abstract class AbstractZeebeAnnotationProcessor implements BeanNameAware {

  private String beanName;

  public String getBeanName() {
    return beanName;
  }

  @Override
  public void setBeanName(final String beanName) {
    this.beanName = beanName;
  }

  public abstract boolean isApplicableFor(ClassInfo beanInfo);

  public abstract void configureFor(final ClassInfo beanInfo);

  public abstract void start(ZeebeClient zeebeClient);

  public abstract void stop(ZeebeClient zeebeClient);
}
