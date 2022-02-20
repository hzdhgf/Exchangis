package com.webank.wedatasphere.exchangis.dss.appconn.operation;

import com.webank.wedatasphere.dss.standard.app.development.operation.RefCreationOperation;
import com.webank.wedatasphere.dss.standard.app.development.ref.CreateRequestRef;
import com.webank.wedatasphere.dss.standard.app.development.ref.NodeRequestRef;
import com.webank.wedatasphere.dss.standard.app.development.service.DevelopmentService;
import com.webank.wedatasphere.dss.standard.app.sso.builder.SSOUrlBuilderOperation;
import com.webank.wedatasphere.dss.standard.app.sso.request.SSORequestOperation;
import com.webank.wedatasphere.dss.standard.common.entity.ref.ResponseRef;
import com.webank.wedatasphere.dss.standard.common.exception.operation.ExternalOperationFailedException;
import com.webank.wedatasphere.exchangis.dss.appconn.constraints.Constraints;
import com.webank.wedatasphere.exchangis.dss.appconn.request.action.ExchangisGetAction;
import com.webank.wedatasphere.exchangis.dss.appconn.request.action.ExchangisPostAction;
import com.webank.wedatasphere.exchangis.dss.appconn.ref.ExchangisCommonResponseRef;
import com.webank.wedatasphere.exchangis.dss.appconn.utils.AppConnUtils;
import org.apache.linkis.httpclient.response.HttpResult;
import org.apache.linkis.server.BDPJettyServerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


public class ExchangisCreationOperation implements RefCreationOperation<CreateRequestRef> {
    private final static Logger logger = LoggerFactory.getLogger(ExchangisCreationOperation.class);

    DevelopmentService developmentService;
    private SSORequestOperation ssoRequestOperation;
    public ExchangisCreationOperation(DevelopmentService developmentService){
        this.developmentService = developmentService;
        this.ssoRequestOperation = this.developmentService.getSSORequestService().createSSORequestOperation(Constraints.EXCHANGIS_APPCONN_NAME);
    }

    @Override
    public ResponseRef createRef(CreateRequestRef createRequestRef) throws ExternalOperationFailedException {
        NodeRequestRef exchangisCreateRequestRef = (NodeRequestRef) createRequestRef;
        exchangisCreateRequestRef.getProjectName();
        ResponseRef responseRef = null;
        logger.info("create job=>projectId:{},jobName:{},nodeType:{}",exchangisCreateRequestRef.getProjectId(),exchangisCreateRequestRef.getName(),exchangisCreateRequestRef.getNodeType());
        if(Constraints.NODE_TYPE_SQOOP.equalsIgnoreCase(exchangisCreateRequestRef.getNodeType())){
             responseRef = sendOffLineRequest(exchangisCreateRequestRef, Constraints.ENGINE_TYPE_SQOOP_NAME);
        }else if(Constraints.NODE_TYPE_DATAX.equalsIgnoreCase(exchangisCreateRequestRef.getNodeType())){
            responseRef = sendOffLineRequest(exchangisCreateRequestRef, Constraints.ENGINE_TYPE_DATAX_NAME);
        }
        return responseRef;
    }

    private ResponseRef sendOffLineRequest(NodeRequestRef requestRef,String engineType)  throws ExternalOperationFailedException {
        String url = getBaseUrl()+"/job";
        logger.info("create sqoop job=>jobContent:{} || projectId:{} || projectName:{} || parameters:{} || type:{}",requestRef.getJobContent(),requestRef.getProjectId(),requestRef.getProjectName(),requestRef.getParameters().toString(),requestRef.getType());
        ExchangisPostAction exchangisPostAction = new ExchangisPostAction();
        String projectName = null;
        try {
            String contextID = requestRef.getJobContent().get("contextID").toString();
            Map contextIDMap =  BDPJettyServerHelper.jacksonJson().readValue(contextID, Map.class);
            String valueJson = contextIDMap.get("value").toString();
            Map map = BDPJettyServerHelper.jacksonJson().readValue(valueJson, Map.class);
            logger.info("map {}",map.toString());
            projectName = map.get("project").toString();
        }catch (Exception e){
            throw new ExternalOperationFailedException(31023, "Get node Id failed!", e);
        }
        String projectId = this.queryProject(requestRef, projectName);
        exchangisPostAction.setUser(requestRef.getUserName());
        exchangisPostAction.addRequestPayload(Constraints.PROJECT_ID,projectId);
        exchangisPostAction.addRequestPayload(Constraints.DSS_PROJECT_ID,requestRef.getJobContent().get("projectId").toString());
        exchangisPostAction.addRequestPayload(Constraints.DSS_PROJECT_NAME,projectName);
        exchangisPostAction.addRequestPayload(Constraints.NODE_ID,requestRef.getJobContent().get("nodeId").toString());
        exchangisPostAction.addRequestPayload(Constraints.NODE_NAME,requestRef.getName());
        exchangisPostAction.addRequestPayload(Constraints.JOB_DESC,requestRef.getJobContent().get("desc").toString());
        exchangisPostAction.addRequestPayload(Constraints.JOB_LABELS, AppConnUtils.serializeDssLabel(requestRef.getDSSLabels()));
        exchangisPostAction.addRequestPayload(Constraints.JOB_NAME,requestRef.getName());
        exchangisPostAction.addRequestPayload(Constraints.JOB_TYPE, Constraints.JOB_TYPE_OFFLINE);
        exchangisPostAction.addRequestPayload(Constraints.ENGINE_TYPE,engineType);

        SSOUrlBuilderOperation ssoUrlBuilderOperation = requestRef.getWorkspace().getSSOUrlBuilderOperation().copy();
        ssoUrlBuilderOperation.setAppName(Constraints.EXCHANGIS_APPCONN_NAME);
        ssoUrlBuilderOperation.setReqUrl(url);
        ssoUrlBuilderOperation.setWorkspace(requestRef.getWorkspace().getWorkspaceName());
        ExchangisCommonResponseRef responseRef;
        try{
            exchangisPostAction.setUrl(ssoUrlBuilderOperation.getBuiltUrl());
            HttpResult httpResult = (HttpResult) this.ssoRequestOperation.requestWithSSO(ssoUrlBuilderOperation, exchangisPostAction);
            logger.info("sendSqoop => body:{}",httpResult.getResponseBody());
            responseRef = new ExchangisCommonResponseRef(httpResult.getResponseBody());
        } catch (Exception e){
            throw new ExternalOperationFailedException(31020, "Create sqoop job Exception", e);
        }

        return responseRef;
    }

    public String queryProject(NodeRequestRef requestRef,String projectName) throws ExternalOperationFailedException{
        String url = getBaseUrl()+"/projects/dss/"+projectName;
        ExchangisGetAction exchangisGetAction = new ExchangisGetAction();
        exchangisGetAction.setUser(requestRef.getUserName());

        SSOUrlBuilderOperation ssoUrlBuilderOperation = requestRef.getWorkspace().getSSOUrlBuilderOperation().copy();
        ssoUrlBuilderOperation.setAppName(Constraints.EXCHANGIS_APPCONN_NAME);
        ssoUrlBuilderOperation.setReqUrl(url);
        ssoUrlBuilderOperation.setWorkspace(requestRef.getWorkspace().getWorkspaceName());
        String projectId ="";
        try{
            exchangisGetAction.setUrl(ssoUrlBuilderOperation.getBuiltUrl());
            HttpResult httpResult = (HttpResult) this.ssoRequestOperation.requestWithSSO(ssoUrlBuilderOperation, exchangisGetAction);
            logger.info("queryProject => body:{} || statusCode:{}",httpResult.getResponseBody(),httpResult.getStatusCode());
            if(httpResult.getStatusCode() == 200){
                Map responseMap = BDPJettyServerHelper.jacksonJson().readValue(httpResult.getResponseBody(), Map.class);
                Map<String, Object> item = (Map<String, Object>) ((Map<String, Object>) responseMap.get("data")).get("item");
                projectId = item.get("id").toString();
            }else{
                throw new ExternalOperationFailedException(31024, "queryProject id null");
            }
            logger.info("queryProject => projectId:{}",projectId);
        } catch (Exception e){
            throw new ExternalOperationFailedException(31020, "queryProject  job Exception", e);
        }


        return projectId;

    }

    private String getBaseUrl(){
        return developmentService.getAppInstance().getBaseUrl() + Constraints.BASEURL;
    }
    @Override
    public void setDevelopmentService(DevelopmentService developmentService) {
        this.developmentService = developmentService;
    }
}
