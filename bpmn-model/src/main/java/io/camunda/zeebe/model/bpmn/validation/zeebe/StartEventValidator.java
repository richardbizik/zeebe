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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import java.util.Collection;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class StartEventValidator implements ModelElementValidator<StartEvent> {
  @Override
  public Class<StartEvent> getElementType() {
    return StartEvent.class;
  }

  @Override
  public void validate(
      final StartEvent element, final ValidationResultCollector validationResultCollector) {
    final Collection<EventDefinition> eventDefinitions = element.getEventDefinitions();

    final Collection<EventDefinition> events =
        eventDefinitions.stream()
            .filter(
                eventDefinition ->
                    eventDefinition
                        .getElementType()
                        .getTypeName()
                        .equals(BpmnModelConstants.BPMN_ELEMENT_SIGNAL_EVENT_DEFINITION))
            .collect(Collectors.toList());

    if (eventDefinitions.size() > 1 && eventDefinitions.size() > events.size()) {
      validationResultCollector.addError(0, "Start event can't have more than one type");
    }
  }
}
