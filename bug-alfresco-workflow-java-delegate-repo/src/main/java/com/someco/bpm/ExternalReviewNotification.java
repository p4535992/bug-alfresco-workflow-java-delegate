package com.someco.bpm;

import java.util.ArrayList;
import java.util.List;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.alfresco.repo.action.executer.MailActionExecuter;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ExternalReviewNotification implements TaskListener {
    private static final long serialVersionUID = 1L;

    private static final String FROM_ADDRESS = "alfresco@localhost";
    private static final String SUBJECT = "Workflow task requires action";
    private static final String RECIP_PROCESS_VARIABLE = "scwf_reviewerEmail";

    private static Log logger = LogFactory.getLog(ExternalReviewNotification.class);

    @Override
    public void notify(DelegateTask task) {
        logger.debug("Inside ExternalReviewNotification.notify()");
        logger.debug("Task ID:" + task.getId());
        logger.debug("Task name:" + task.getName());
        logger.debug("Task proc ID:" + task.getProcessInstanceId());
        logger.debug("Task def key:" + task.getTaskDefinitionKey());

        WorkflowService ws = getServiceRegistry().getWorkflowService();
        WorkflowInstance wi = null;
        
        List<WorkflowInstance> lwi = ws.getActiveWorkflows();
        for(int i = 0; i < lwi.size(); i++) {
        	if(lwi.get(i).getId().substring(9, lwi.get(i).getId().length()).equals(task.getExecutionId())) {
        		wi = lwi.get(i);
        	}
        }

        NodeRef nodeRefWorkflow = wi.getWorkflowPackage();
        NodeService nodeService = getServiceRegistry().getNodeService();

        List<ChildAssociationRef> listaChildAssoc = nodeService.getChildAssocs(nodeRefWorkflow);
        List<String> listaNodeRefDocumenti = new ArrayList<String>();
        for(int i = 0; i < listaChildAssoc.size(); i++) {
        	listaNodeRefDocumenti.add(listaChildAssoc.get(i).getChildRef().toString());
        }

        String recipient = (String) task.getVariable(ExternalReviewNotification.RECIP_PROCESS_VARIABLE);

        StringBuffer sb = new StringBuffer();
        sb.append("You have been assigned to a task named ");
        sb.append(task.getName());

        sb.append(".\r\n\r\n");
        sb.append("Please, read the document(s) by clicking the following link(s):\r\n\r\n");
        for(int i = 0; i < listaNodeRefDocumenti.size(); i++) {
            sb.append("http://192.168.1.159:8080/share/page/document-details?nodeRef=" + listaNodeRefDocumenti.get(i) +"\r\n\r\n\r\n\r\n");
        }

        sb.append("After reading the document(s), please take the appropriate action by clicking one of the links below:\r\n\r\n");     
        sb.append(getLink(task.getId(), "Approve"));
        sb.append(getLink(task.getId(), "Reject"));

        logger.debug("Message body:" + sb.toString());

        ActionService actionService = getServiceRegistry().getActionService();
        Action mailAction = actionService.createAction(MailActionExecuter.NAME);

        mailAction.setParameterValue(MailActionExecuter.PARAM_SUBJECT, ExternalReviewNotification.SUBJECT);        
        mailAction.setParameterValue(MailActionExecuter.PARAM_TO, recipient);
        mailAction.setParameterValue(MailActionExecuter.PARAM_FROM, ExternalReviewNotification.FROM_ADDRESS);
        mailAction.setParameterValue(MailActionExecuter.PARAM_TEXT, sb.toString());
        
        actionService.executeAction(mailAction, null);
        
        logger.debug("Mail action executed");
        
        return;
    }

    public String getLink(String taskId, String outcome) {
        StringBuffer sb = new StringBuffer();
        sb.append("\r\n");
        sb.append("http://localhost:8080/alfresco/service/someco/bpm/review?id=activiti$");
        sb.append(taskId);
        sb.append("&action=");
        sb.append(outcome);
        sb.append("&guest=true");
        sb.append("\r\n\r\n");
        return sb.toString();
    }

    /* taken from ActivitiScriptBase.java */
    protected ServiceRegistry getServiceRegistry() {
        ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
        if (config != null) {
            // Fetch the registry that is injected in the activiti spring-configuration
            ServiceRegistry registry = (ServiceRegistry) config.getBeans().get(ActivitiConstants.SERVICE_REGISTRY_BEAN_KEY);
            if (registry == null) {
                throw new RuntimeException(
                            "Service-registry not present in ProcessEngineConfiguration beans, expected ServiceRegistry with key" + 
                            ActivitiConstants.SERVICE_REGISTRY_BEAN_KEY);
            }
            return registry;
        }
        throw new IllegalStateException("No ProcessEngineCOnfiguration found in active context");
    }
}