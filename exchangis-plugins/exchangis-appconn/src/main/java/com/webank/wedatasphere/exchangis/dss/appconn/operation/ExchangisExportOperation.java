package com.webank.wedatasphere.exchangis.dss.appconn.operation;

import com.webank.wedatasphere.dss.standard.app.development.operation.RefExportOperation;
import com.webank.wedatasphere.dss.standard.app.development.ref.ExportRequestRef;
import com.webank.wedatasphere.dss.standard.app.development.service.DevelopmentService;
import com.webank.wedatasphere.dss.standard.app.sso.builder.SSOUrlBuilderOperation;
import com.webank.wedatasphere.dss.standard.app.sso.request.SSORequestOperation;
import com.webank.wedatasphere.dss.standard.common.entity.ref.ResponseRef;
import com.webank.wedatasphere.dss.standard.common.exception.operation.ExternalOperationFailedException;
import com.webank.wedatasphere.exchangis.dss.appconn.constraints.Constraints;
import com.webank.wedatasphere.exchangis.dss.appconn.request.action.ExchangisGetAction;
import com.webank.wedatasphere.exchangis.dss.appconn.ref.ExchangisCommonResponseRef;
import org.apache.linkis.httpclient.response.HttpResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExchangisExportOperation implements RefExportOperation<ExportRequestRef> {
    private final static Logger logger = LoggerFactory.getLogger(ExchangisExportOperation.class);

    private DevelopmentService developmentService;
    private SSORequestOperation ssoRequestOperation;

    public ExchangisExportOperation(DevelopmentService developmentService){
        this.developmentService = developmentService;
        this.ssoRequestOperation = this.developmentService.getSSORequestService().createSSORequestOperation(Constraints.EXCHANGIS_APPCONN_NAME);
    }


    @Override
    public ResponseRef exportRef(ExportRequestRef exportRequestRef) throws ExternalOperationFailedException {
        String url = getBaseUrl()  + "/export";
        logger.info("importRef job=>parameter:{}",exportRequestRef.getParameters().toString());

        ExchangisGetAction exchangisGetAction = new ExchangisGetAction();
        exchangisGetAction.setUser(exportRequestRef.getParameter("user").toString());
        exchangisGetAction.setParameter("projectId",exportRequestRef.getParameter("projectId"));

        SSOUrlBuilderOperation ssoUrlBuilderOperation = exportRequestRef.getWorkspace().getSSOUrlBuilderOperation().copy();
        ssoUrlBuilderOperation.setAppName(Constraints.EXCHANGIS_APPCONN_NAME);
        ssoUrlBuilderOperation.setReqUrl(url);
        ssoUrlBuilderOperation.setWorkspace(exportRequestRef.getWorkspace().getWorkspaceName());
        ResponseRef responseRef;
        try{
            exchangisGetAction.setUrl(ssoUrlBuilderOperation.getBuiltUrl());
            HttpResult httpResult = (HttpResult) this.ssoRequestOperation.requestWithSSO(ssoUrlBuilderOperation, exchangisGetAction);
            responseRef = new ExchangisCommonResponseRef(httpResult.getResponseBody());
        } catch (Exception e){
            throw new ExternalOperationFailedException(31022, "export job Exception", e);
        }
        return responseRef;
    }

    @Override
    public void setDevelopmentService(DevelopmentService developmentService) {
        this.developmentService = developmentService;
    }

    private String getBaseUrl(){
        return developmentService.getAppInstance().getBaseUrl() + Constraints.BASEURL;
    }
}
