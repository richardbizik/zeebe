/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.QueryValidator;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static io.camunda.operate.webapp.api.v1.rest.DecisionDefinitionController.URI;

@RestController("DecisionDefinitionControllerV1")
@RequestMapping(URI)
@Tag(name = "DecisionDefinition", description = "Decision definition API")
@Validated
public class DecisionDefinitionController extends ErrorController
    implements SearchController<DecisionDefinition> {

  public static final String URI = "/v1/decision-definitions";

  @Autowired
  private DecisionDefinitionDao decisionDefinitionDao;

  private final QueryValidator<DecisionDefinition> queryValidator = new QueryValidator<>();

  private static final QueryValidator.CustomQueryValidator<DecisionDefinition> searchSortValidator = query -> {
    List<Query.Sort> sorts = query.getSort();
    if (sorts != null) {
      for (Query.Sort sort : sorts) {
        String field = sort.getField();
        if (DecisionDefinition.DECISION_REQUIREMENTS_NAME.equals(field) || DecisionDefinition.DECISION_REQUIREMENTS_VERSION.equals(field)) {
          throw new ValidationException(String.format("Field '%s' cannot be used as sort field", field));
        }
      }
    }
  };

  @Operation(summary = "Get decision definition by key", security = { @SecurityRequirement(name = "bearer-key"), @SecurityRequirement(name = "cookie") }, tags = {
      "Decision" }, responses = { @ApiResponse(description = "Success", responseCode = "200"),
      @ApiResponse(description = ServerException.TYPE, responseCode = "500", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ClientException.TYPE, responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ResourceNotFoundException.TYPE, responseCode = "404", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))) })
  @Override
  public DecisionDefinition byKey(Long key) {
    return decisionDefinitionDao.byKey(key);
  }

  @Operation(summary = "Search decision definitions", security = { @SecurityRequirement(name = "bearer-key"), @SecurityRequirement(name = "cookie") }, tags = {
      "Decision" }, responses = { @ApiResponse(description = "Success", responseCode = "200"),
      @ApiResponse(description = ServerException.TYPE, responseCode = "500", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ClientException.TYPE, responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))),
      @ApiResponse(description = ValidationException.TYPE, responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))) })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search examples", content = @Content(examples = {
      @ExampleObject(name = "All", value = "{}", description = "All decision definitions (default size is 10)"),
      @ExampleObject(name = "Size of returned list", value = "{ \"size\": 5 }", description = "Search decision definitions and return list of size 5"),
      @ExampleObject(name = "Sort", value = "{ \"sort\": [{\"field\":\"name\",\"order\": \"ASC\"}] }", description = "Search decision definitions and sort ascending by name"),
      @ExampleObject(name = "Sort and size", value = "{ \"size\": 5, \"sort\": [{\"field\":\"name\",\"order\": \"DESC\"}] }", description = "Search decision definitions, sort descending by name, and return list of size 5"),
      @ExampleObject(name = "Sort and page", value = "{   \"size\": 5," + "    \"sort\": [{\"field\":\"name\",\"order\": \"ASC\"}]," + "    \"searchAfter\": ["
          + "      \"Decide the Dish\"," + "      \"2251799813686550\"" + "  ] }", description =
          "Search decision definitions, sort ascending by name, and return page of size 5.\n"
              + "To get the next page, copy the value of 'sortValues' into 'searchAfter' value.\n"
              + "Sort specification should match the searchAfter specification."),
      @ExampleObject(name = "Filter and sort ", value = "{   \"filter\": {" + "      \"version\": 1" + "    }," + "    \"size\": 50,"
          + "    \"sort\": [{\"field\":\"decisionId\",\"order\": \"ASC\"}]}", description = "Filter by version and sort by decisionId"), }))
  @Override
  public Results<DecisionDefinition> search(@RequestBody final Query<DecisionDefinition> query) {
    queryValidator.validate(query, DecisionDefinition.class, searchSortValidator);
    return decisionDefinitionDao.search(query);
  }
}