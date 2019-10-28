package io.leangen.graphql.spqr.spring.web;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;
import io.leangen.graphql.spqr.spring.web.dto.GraphQLRequest;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

@RestController
public abstract class GraphQLController<R> {

    @Autowired
    private ObjectMapper objectMapper;

    protected final GraphQL graphQL;
    protected final GraphQLExecutor<R> executor;

    public GraphQLController(GraphQL graphQL, GraphQLExecutor<R> executor) {
        this.graphQL = graphQL;
        this.executor = executor;
    }

    @PostMapping(
            value = "${graphql.spqr.http.endpoint:/graphql}",
            consumes = {MediaType.APPLICATION_JSON_UTF8_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ResponseBody
    public Object executeJsonPost(@RequestBody GraphQLRequest requestBody,
                                GraphQLRequest requestParams,
                                R request) {
        String query = requestParams.getQuery() == null ? requestBody.getQuery() : requestParams.getQuery();
        String operationName = requestParams.getOperationName() == null ? requestBody.getOperationName() : requestParams.getOperationName();
        Map<String, Object> variables = requestParams.getVariables() == null ? requestBody.getVariables() : requestParams.getVariables();

        return executor.execute(graphQL, new GraphQLRequest(query, operationName, variables), request);
    }

    @PostMapping(
            value = "${graphql.spqr.http.endpoint:/graphql}",
            consumes = {"application/graphql", "application/graphql;charset=UTF-8"},
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ResponseBody
    public Object executeGraphQLPost(@RequestBody String queryBody,
                                   GraphQLRequest graphQLRequest,
                                   R request) {
        String query = graphQLRequest.getQuery() == null ? queryBody : graphQLRequest.getQuery();
        return executor.execute(graphQL, new GraphQLRequest(query, graphQLRequest.getOperationName(), graphQLRequest.getVariables()), request);
    }

    @RequestMapping(
            method = RequestMethod.POST,
            value = "${graphql.spqr.http.endpoint:/graphql}",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, "application/x-www-form-urlencoded;charset=UTF-8"},
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    @ResponseBody
    public Object executeFormPost(@RequestParam Map<String, String> queryParams,
                                GraphQLRequest graphQLRequest,
                                R request) {
        String queryParam = queryParams.get("query");
        String operationNameParam = queryParams.get("operationName");

        String query = StringUtils.isEmpty(queryParam) ? graphQLRequest.getQuery() : queryParam;
        String operationName = StringUtils.isEmpty(operationNameParam) ? graphQLRequest.getOperationName() : operationNameParam;

        return executor.execute(graphQL, new GraphQLRequest(query, operationName, graphQLRequest.getVariables()), request);
    }

    @GetMapping(
            value = "${graphql.spqr.http.endpoint:/graphql}",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
            headers = "Connection!=Upgrade"
    )
    @ResponseBody
    public Object executeGet(GraphQLRequest graphQLRequest, R request) {
        return executor.execute(graphQL, graphQLRequest, request);
    }

    @PostMapping(
            value = "${graphql.spqr.http.endpoint:/graphql}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    public Object executeMultipartFileUpload(
            @RequestParam("operations") String requestString,
            @RequestParam("map") String mappingString,
            @RequestParam Map<String, MultipartFile> multipartFiles,
            R request) throws IOException {
        GraphQLRequest graphQLRequest = objectMapper.readValue(requestString, GraphQLRequest.class);
        //noinspection unchecked
        Map<String, List<String>> fileMappings = objectMapper.readValue(mappingString, Map.class);

        Map<String, Object> values = new LinkedHashMap<>();
        fileMappings.forEach((fileKey, variables) -> {
            for (String variable : variables) {
                String[] parts = variable.split("\\.");
                String path = parts[0] + Arrays.stream(parts).skip(1).collect(Collectors.joining("][", "[", "]"));
                values.put(path,  multipartFiles.get(fileKey));
            }
        });

        DataBinder binder = new DataBinder(graphQLRequest, "operations");
        binder.setIgnoreUnknownFields(false);
        binder.setIgnoreInvalidFields(false);
        binder.bind(new MutablePropertyValues(values));

        return executor.execute(graphQL, graphQLRequest, request);
    }
}
