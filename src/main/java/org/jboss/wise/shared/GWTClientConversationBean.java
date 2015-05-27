/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.wise.shared;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jboss.wise.core.client.builder.WSDynamicClientBuilder;
import org.jboss.wise.core.client.impl.reflection.builder.ReflectionBasedWSDynamicClientBuilder;
import org.jboss.wise.gui.ClientConversationBean;
import org.jboss.wise.gui.ClientHelper;
import org.jboss.wise.gui.model.TreeNode;
import org.jboss.wise.gui.model.TreeNodeImpl;
import org.jboss.wise.gwt.shared.Service;
import org.jboss.wise.gwt.shared.tree.element.ComplexTreeElement;
import org.jboss.wise.gwt.shared.tree.element.EnumerationTreeElement;
import org.jboss.wise.gwt.shared.tree.element.GroupTreeElement;
import org.jboss.wise.gwt.shared.tree.element.ParameterizedTreeElement;
import org.jboss.wise.gwt.shared.tree.element.RequestResponse;
import org.jboss.wise.gwt.shared.tree.element.SimpleTreeElement;
import org.jboss.wise.gwt.shared.tree.element.TreeElement;
import org.jboss.wise.gwt.shared.tree.element.TreeElementFactory;
import org.jboss.wise.gui.treeElement.ComplexWiseTreeElement;
import org.jboss.wise.gui.treeElement.EnumerationWiseTreeElement;
import org.jboss.wise.gui.treeElement.GroupWiseTreeElement;
import org.jboss.wise.gui.treeElement.LazyLoadWiseTreeElement;
import org.jboss.wise.gui.treeElement.ParameterizedWiseTreeElement;
import org.jboss.wise.gui.treeElement.SimpleWiseTreeElement;
import org.jboss.wise.gui.treeElement.WiseTreeElement;

import org.jboss.logging.Logger;

/**
 * User: rsearls
 * Date: 4/2/15
 */
public class GWTClientConversationBean extends ClientConversationBean {

   private static final long serialVersionUID = 4531727535065189366L;
   private static Logger log = Logger.getLogger(GWTClientConversationBean.class);
   private Map<String, WiseTreeElement> treeElementMap = new HashMap<String, WiseTreeElement>();
   private HashMap<String, WiseTreeElement> lazyLoadMap = new HashMap<String, WiseTreeElement>();
   private WsdlFinder wsdlFinder = null;


   public void readWsdl() {

      cleanup();

      try {

         WSDynamicClientBuilder builder = new ReflectionBasedWSDynamicClientBuilder()
            .verbose(true).messageStream(ps).keepSource(true).excludeNonSOAPPorts(true)
            .maxThreadPoolSize(1);

         String wsdlUser = getWsdlUser();
         String wsdlPwd = getWsdlPwd();

         builder.userName(wsdlUser);
         setInvocationUser(wsdlUser);
         builder.password(wsdlPwd);
         setInvocationPwd(wsdlPwd);
         client = builder.wsdlURL(getWsdlUrl()).build();

      } catch (Exception e) {
         setError("Could not read WSDL from specified URL. Please check credentials and see logs for further information.");
         logException(e);
      }
      if (client != null) {
         try {
            List<Service> services = ClientHelper.convertServicesToGui(client.processServices());
            String currentOperation = ClientHelper.getFirstGuiOperation(services);

            setServices(services);
            setCurrentOperation(currentOperation);

         } catch (Exception e) {
            setError("Could not parse WSDL from specified URL. Please check logs for further information.");
            logException(e);
         }
      }
   }

   public RequestResponse parseOperationParameters(String curOperation) {

      try {
         List<Service> services = getServices();
         String currentOperation = getCurrentOperation();

         String currentOperationFullName = ClientHelper.getOperationFullName(currentOperation, services);
         TreeNodeImpl inputTree = ClientHelper.convertOperationParametersToGui(ClientHelper.getWSMethod(curOperation, client), client);

         setInputTree(inputTree);
         setCurrentOperationFullName(currentOperationFullName);

      } catch (Exception e) {
         e.printStackTrace();
      }

      TreeElement treeElement = wiseDataPostProcess((TreeNodeImpl)getInputTree());

      RequestResponse invResult = new RequestResponse();
      invResult.setOperationFullName(getCurrentOperationFullName());
      invResult.setTreeElement(treeElement);

      return invResult;
   }

   public String generateRequestPreview(TreeElement rootTreeElement) {
      userDataPostProcess(rootTreeElement);
      generateRequestPreview();
      return getRequestPreview();
   }

   public RequestResponse performInvocation(TreeElement root) {
      userDataPostProcess(root);
      performInvocation();

      TreeElement treeE = null;
      TreeNodeImpl outputTree = getOutputTree();
      if (outputTree != null) {
         treeE = wiseOutputPostProcess(outputTree);
      }

      RequestResponse invResult = new RequestResponse();
      invResult.setOperationFullName(getCurrentOperationFullName());
      invResult.setResponseMessage(getResponseMessage());
      invResult.setTreeElement(treeE);
      invResult.setErrorMessage(getError());

      return invResult;
   }

   /**
    * Generate GWT objects that correspond to WISE objects
    *
    * @param tNode
    * @return
    */
   private TreeElement wiseDataPostProcess(TreeNodeImpl tNode) {

      lazyLoadMap.clear();

      SimpleTreeElement treeElement = new SimpleTreeElement();
      List<TreeElement> children = treeElement.getChildren();

      Iterator<Object> keyIt = tNode.getChildrenKeysIterator();
      while (keyIt.hasNext()) {
         WiseTreeElement child = (WiseTreeElement) tNode.getChild(keyIt.next());
         TreeElement te = wiseDataTransfer(child);
         children.add(te);
      }

      return treeElement;
   }

   private TreeElement wiseDataTransfer(WiseTreeElement wte) {

      TreeElement treeElement = TreeElementFactory.create(wte);

      if (treeElement instanceof GroupTreeElement) {
         GroupTreeElement gTreeElement = (GroupTreeElement)treeElement;
         WiseTreeElement protoType =  ((GroupWiseTreeElement) wte).getPrototype();

         TreeElement tElement = wiseDataTransfer(protoType);
         gTreeElement.setProtoType(tElement);

         String rType = gTreeElement.getCleanClassName(
            ((ParameterizedType)wte.getClassType()).getRawType().toString());
         gTreeElement.setClassType(rType);

      } else if (wte instanceof ComplexWiseTreeElement) {
         ComplexWiseTreeElement cNode = (ComplexWiseTreeElement)wte;
         Iterator<Object> keyIt = cNode.getChildrenKeysIterator();
         while (keyIt.hasNext()) {
            WiseTreeElement child = (WiseTreeElement) cNode.getChild(keyIt.next());
            TreeElement te = wiseDataTransfer(child);
            te.setId(child.getId().toString());
            treeElement.addChild(te);

         }
         //treeElement.setClassType(treeElement.getCleanClassName(wte.getClassType().toString()));
         lazyLoadMap.put(cNode.getClassType().toString(), cNode);

      } else if (wte instanceof ParameterizedWiseTreeElement) {
         ParameterizedWiseTreeElement cNode = (ParameterizedWiseTreeElement)wte;
         Iterator<Object> keyIt = cNode.getChildrenKeysIterator();
         while (keyIt.hasNext()) {
            WiseTreeElement child = (WiseTreeElement) cNode.getChild(keyIt.next());
            TreeElement te = wiseDataTransfer(child);
            te.setId(child.getId().toString());
            treeElement.addChild(te);

         }
         //treeElement.setClassType(treeElement.getCleanClassName(wte.getClassType().toString()));

      } else if (treeElement instanceof EnumerationTreeElement) {
         EnumerationTreeElement eTreeElement = (EnumerationTreeElement)treeElement;
         Map<String, String> eValuesMap = ((EnumerationWiseTreeElement)wte).getValidValue();
         if (eValuesMap != null) {
            eTreeElement.getEnumValues().addAll(eValuesMap.keySet());
         }
         eTreeElement.setValue(((SimpleWiseTreeElement) wte).getValue());
         //eTreeElement.setClassType(treeElement.getCleanClassName(wte.getClassType().toString()));

      } else {
         if (wte instanceof SimpleWiseTreeElement) {
            ((SimpleTreeElement) treeElement).setValue(((SimpleWiseTreeElement) wte).getValue());
         }

         //treeElement.setClassType(treeElement.getCleanClassName(wte.getClassType().toString()));

      }

      // classType check facilitates automated testing
      if (!(treeElement instanceof GroupTreeElement) && (wte.getClassType() != null)) {
         treeElement.setClassType(treeElement.getCleanClassName(wte.getClassType().toString()));
      }

      treeElement.setName(wte.getName());
      treeElement.setKind(wte.getKind());
      treeElement.setId(Integer.toString(((Object) wte).hashCode()));
      treeElementMap.put(treeElement.getId(), wte);

      return treeElement;
   }


   /**
    * Generate GWT objects from WISE response objects
    *
    * @param tNode
    * @return
    */
   private TreeElement wiseOutputPostProcess(TreeNodeImpl tNode) {

      SimpleTreeElement treeElement = new SimpleTreeElement();

      if(tNode == null){
         log.error("wiseOutputPostProcess tNode is NULL");

      } else {
         List<TreeElement> children = treeElement.getChildren();

         Iterator<Object> keyIt = tNode.getChildrenKeysIterator();
         while (keyIt.hasNext()) {
            WiseTreeElement child = (WiseTreeElement) tNode.getChild(keyIt.next());
            TreeElement te = wiseOutputTransfer(child);
            children.add(te);
         }
      }
      return treeElement;
   }

   private TreeElement wiseOutputTransfer(WiseTreeElement wte) {

      TreeElement treeElement = TreeElementFactory.create(wte);

      if (treeElement instanceof GroupTreeElement) {
         GroupTreeElement gTreeElement = (GroupTreeElement)treeElement;
         WiseTreeElement protoType =  ((GroupWiseTreeElement) wte).getPrototype();

         // test for characteristic of parameterizedType
         if (protoType != null) {
            TreeElement pElement = wiseOutputTransfer(protoType);
            gTreeElement.setProtoType(pElement);
         }

         Type[] typeArr = ((ParameterizedType)wte.getClassType()).getActualTypeArguments();
         if (typeArr != null && typeArr.length > 0) {
            String actualType = typeArr[0].toString();
            gTreeElement.setRawType(actualType);
         } else {
            log.error("ERROR parameterizedType actualTypeArguments not found for "
               + wte.getName());
         }

         String rType = gTreeElement.getCleanClassName(
            ((ParameterizedType)wte.getClassType()).getRawType().toString());
         gTreeElement.setClassType(rType);


         GroupWiseTreeElement gChild = (GroupWiseTreeElement)wte;
         Iterator<Object> childKeyIt = gChild.getChildrenKeysIterator();
         while (childKeyIt.hasNext()) {
            WiseTreeElement c = (WiseTreeElement) gChild.getChild(childKeyIt.next());
            TreeElement te = wiseOutputTransfer(c);
            gTreeElement.addValue(te);
         }

      } else if (wte instanceof ComplexWiseTreeElement) {
         ComplexWiseTreeElement cNode = (ComplexWiseTreeElement) wte;
         Iterator<Object> keyIt = cNode.getChildrenKeysIterator();
         while (keyIt.hasNext()) {
            WiseTreeElement child = (WiseTreeElement) cNode.getChild(keyIt.next());
            TreeElement te = wiseOutputTransfer(child);
            treeElement.addChild(te);
         }

         treeElement.setClassType(cNode.getClassType().toString());

      } else if (wte instanceof ParameterizedWiseTreeElement) {
         ParameterizedWiseTreeElement cNode = (ParameterizedWiseTreeElement) wte;
         Iterator<Object> keyIt = cNode.getChildrenKeysIterator();
         while (keyIt.hasNext()) {
            WiseTreeElement child = (WiseTreeElement) cNode.getChild(keyIt.next());
            TreeElement te = wiseOutputTransfer(child);
            treeElement.addChild(te);
         }

      } else if (treeElement instanceof EnumerationTreeElement) {
         EnumerationTreeElement eTreeElement = (EnumerationTreeElement)treeElement;
         Map<String, String> eValuesMap = ((EnumerationWiseTreeElement)wte).getValidValue();
         if (eValuesMap != null) {
            eTreeElement.getEnumValues().addAll(eValuesMap.keySet());
         }
         eTreeElement.setValue(((SimpleWiseTreeElement) wte).getValue());

         // classType check facilitates automated testing
         if (wte.getClassType() != null) {
            eTreeElement.setClassType(treeElement.getCleanClassName(wte.getClassType().toString()));
         }

      } else {
         if (wte instanceof SimpleWiseTreeElement) {
            ((SimpleTreeElement) treeElement).setValue(((SimpleWiseTreeElement) wte).getValue());

         }

         // classType check facilitates automated testing
         if (wte.getClassType() != null) {
            treeElement.setClassType(treeElement.getCleanClassName(wte.getClassType().toString()));
         }

      }

      treeElement.setName(wte.getName());
      treeElement.setKind(wte.getKind());
      treeElement.setId(Integer.toString(((Object)wte).hashCode()));
      treeElementMap.put(treeElement.getId(), wte);

      return treeElement;
   }

   /**
    * Transfer user input data from GWT objects to WISE objects
    * @param root
    */
   private void userDataPostProcess (TreeElement root) {
      if (root != null) {
         for(TreeElement te : root.getChildren()) {
            WiseTreeElement wte = treeElementMap.get(te.getId());
            if (wte == null) {
               // This should never happen
               log.error("ERROR: not WiseTreeElement for TreeElement");

            } else {
               userDataTransfer(te, wte);
            }
         }
      }
   }


   public void userDataTransfer(TreeElement treeElement, WiseTreeElement wte) {

      if (TreeElement.SIMPLE.equals(treeElement.getKind())) {

         if (wte instanceof SimpleWiseTreeElement) {
            ((SimpleWiseTreeElement) wte).setValue(((SimpleTreeElement) treeElement).getValue());
            wte.setNil(false);

         } else {
            log.error("ERROR: incompatible types. TreeElement: " + treeElement.getKind()
               + "  WiseTreeElement: " + wte.getClass().getName());
         }

      } else  if (treeElement instanceof ComplexTreeElement) {

         if (wte instanceof ComplexWiseTreeElement) {
            ComplexTreeElement cte = (ComplexTreeElement) treeElement;
            ComplexWiseTreeElement cWise = (ComplexWiseTreeElement) wte;

            Iterator<Object> childKeyIt = cWise.getChildrenKeysIterator();
            // create structure for node lookup by variable name
            HashMap<String, TreeNode> wiseChildren = new HashMap<String, TreeNode>();
            while (childKeyIt.hasNext()) {
               TreeNode tNode = cWise.getChild(childKeyIt.next());
               wiseChildren.put(((WiseTreeElement)tNode).getName(), tNode);
            }

            int cnt = cte.getChildren().size();
            if (cnt == wiseChildren.size()) {
               for (int i = 0; i < cnt; i++) {
                  TreeNode tNode = wiseChildren.get(cte.getChildren().get(i).getName());

                  if (tNode != null) {
                     userDataTransfer(cte.getChildren().get(i), (WiseTreeElement) tNode);
                  } else {
                     log.error("ERROR: No Wise treeNode found for name: " + cte.getChildren().get(i).getName());
                  }
               }

            } else {
               log.error("ERROR: incompatable child count: ComplexTreeElement cnt: "
                  + cte.getChildren().size() + "  ComplexWiseTreeElement cnt: " + wiseChildren.size());
            }

         } else {
            log.error("ERROR: incompatible types. TreeElement: " + treeElement.getKind()
               + "  WiseTreeElement: " + wte.getClass().getName());
         }

      } else if (treeElement instanceof ParameterizedTreeElement) {

         if (wte instanceof ParameterizedWiseTreeElement) {
            ParameterizedTreeElement cte = (ParameterizedTreeElement) treeElement;
            ParameterizedWiseTreeElement cWise = (ParameterizedWiseTreeElement) wte;

            Iterator<Object> childKeyIt = cWise.getChildrenKeysIterator();
            // create structure for node lookup by variable name
            HashMap<String, TreeNode> wiseChildren = new HashMap<String, TreeNode>();
            while (childKeyIt.hasNext()) {
               TreeNode tNode = cWise.getChild(childKeyIt.next());
               wiseChildren.put(((WiseTreeElement)tNode).getName(), tNode);
            }

            int cnt = cte.getChildren().size();
            if (cnt == wiseChildren.size()) {
               for (int i = 0; i < cnt; i++) {
                  TreeNode tNode = wiseChildren.get(cte.getChildren().get(i).getName());

                  if (tNode != null) {
                     userDataTransfer(cte.getChildren().get(i), (WiseTreeElement) tNode);
                  } else {
                     log.error("ERROR: No Wise treeNode found for name: " + cte.getChildren().get(i).getName());
                  }
               }

            } else {
               log.error("ERROR: incompatable child count: ParameterizedTreeElement cnt: "
                  + cte.getChildren().size() + "  ParameterizedWiseTreeElement cnt: " + wiseChildren.size());
            }

         } else {
            log.error("ERROR: incompatible types. TreeElement: " + treeElement.getKind()
               + "  WiseTreeElement: " + wte.getClass().getName());
         }

      } else if (treeElement instanceof GroupTreeElement) {

         if (wte instanceof GroupWiseTreeElement) {
            GroupTreeElement cte = (GroupTreeElement)treeElement;
            GroupWiseTreeElement cWise = (GroupWiseTreeElement)wte;

            // Must use separate key list to void ConcurrentModificationException
            Iterator<Object> keyIt = cWise.getChildrenKeysIterator();
            List<Object> keyList = new ArrayList<Object>();
            while (keyIt.hasNext()) {
               keyList.add(keyIt.next());
            }
            // remove pre-existing protoType instances.
            for (Object key : keyList) {
               cWise.removeChild(key);
            }

            // replace deferred class with actual class.
            if (cWise.getPrototype() instanceof LazyLoadWiseTreeElement) {
               WiseTreeElement protoChildWte = lazyLoadMap.get(cWise.getPrototype().getClassType().toString());
               WiseTreeElement clone = protoChildWte.clone();
               cWise.setPrototype(clone);
            }

            for(TreeElement child : cte.getValueList()) {
               WiseTreeElement protoChildWte = cWise.incrementChildren();
               userDataTransfer(child, protoChildWte);
            }

         } else {
            log.error("ERROR: incompatible types. TreeElement: " + treeElement.getKind()
               + "  WiseTreeElement: " + wte.getClass().getName());
         }

      } else if (treeElement instanceof EnumerationTreeElement) {
         if (wte instanceof EnumerationWiseTreeElement) {

            ((EnumerationWiseTreeElement) wte).setValue(((EnumerationTreeElement) treeElement).getValue());
            wte.setNil(treeElement.isNil());

         } else {
            log.error("ERROR: incompatible types. TreeElement: " + treeElement.getKind()
               + "  WiseTreeElement: " + wte.getClass().getName());
         }

      }
   }

   /**
    * check of deployed wsdls on the server.
    * @return
    */
   public List<String> getWsdlList () {
      if (wsdlFinder == null) {
         wsdlFinder = new WsdlFinder();
      }
      return wsdlFinder.getWsdlList();
   }


   @Override
   protected void cleanup() {
      super.cleanup();
      treeElementMap.clear();
   }

   /**
    * Method faciliates testing
    * @param tNode
    * @return
    */
   public TreeElement testItWiseOutputPostProcess (TreeNodeImpl tNode) {
      return wiseOutputPostProcess(tNode);
   }

   /**
    * Method faciliates testing
    * @param tNode
    * @return
    */
   public TreeElement testItWiseDataPostProcess (TreeNodeImpl tNode) {
      return wiseDataPostProcess(tNode);
   }

   /**
    * Method faciliates testing
    * @param treeElement
    * @param wte
    */
   public void testItUserDataTransfer (TreeElement treeElement, WiseTreeElement wte) {
      userDataTransfer(treeElement, wte);
   }
}
