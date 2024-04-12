/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.rest;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.MockMvcManager;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false"
    })
@WebAppConfiguration
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
public class ProcessInstanceRestServiceIT {
  @Autowired MockMvcManager mockMvcManager;

  @Test
  public void testGetInstanceByIdWithInvalidId() throws Exception {
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/4503599627535750:";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetIncidentsByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/incidents";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetVariablesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/variables";
    final MvcResult mvcResult =
        mockMvcManager.postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeStatesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-states";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeMetadataByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-metadata";
    final MvcResult mvcResult =
        mockMvcManager.postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }
}
