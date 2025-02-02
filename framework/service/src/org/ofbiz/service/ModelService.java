/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.service;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.wsdl.Binding;
import javax.wsdl.BindingInput;
import javax.wsdl.BindingOperation;
import javax.wsdl.BindingOutput;
import javax.wsdl.Definition;
import javax.wsdl.Input;
import javax.wsdl.Message;
import javax.wsdl.Operation;
import javax.wsdl.Output;
import javax.wsdl.Part;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.wsdl.Service;
import javax.wsdl.Types;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.extensions.soap.SOAPBody;
import javax.wsdl.extensions.soap.SOAPOperation;
import javax.wsdl.factory.WSDLFactory;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.ofbiz.base.location.FlexibleLocation;
import org.ofbiz.base.metrics.Metrics;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.ObjectType;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilCodec;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.service.group.GroupModel;
import org.ofbiz.service.group.GroupServiceModel;
import org.ofbiz.service.group.ServiceGroupReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.wsdl.extensions.soap.SOAPAddressImpl;
import com.ibm.wsdl.extensions.soap.SOAPBindingImpl;
import com.ibm.wsdl.extensions.soap.SOAPBodyImpl;
import com.ibm.wsdl.extensions.soap.SOAPOperationImpl;

/**
 * Generic Service Model Class.
 *
 * <p>SCIPIO: 3.0.0: Getters added for member fields which were public in legacy code; should be preferred; improved {@link #makeValid}.</p>
 */
@SuppressWarnings("serial")
public class ModelService extends AbstractMap<String, Object> implements Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    private static final Field[] MODEL_SERVICE_FIELDS;
    private static final Map<String, Field> MODEL_SERVICE_FIELD_MAP;
    static {
        MODEL_SERVICE_FIELDS = ModelService.class.getFields();
        Map<String, Field> fieldMap = new LinkedHashMap<>();
        for (Field field : MODEL_SERVICE_FIELDS) {
            fieldMap.put(field.getName(), field);
        }
        MODEL_SERVICE_FIELD_MAP = Collections.unmodifiableMap(fieldMap);
    }

    public static final String XSD = "http://www.w3.org/2001/XMLSchema";
    public static final String TNS = "http://ofbiz.apache.org/service/";

    public static final String resource = "ServiceErrorUiLabels";

    /**
     * Service parameter mode representing input fields, for use with {@link #makeValid}.
     *
     * <p>NOTE: When specifying to {@link #makeValid}, it's best to simply hardcode the string for brevity and imports.</p>
     *
     * <p>SCIPIO: 3.0.0: Added special *-SYS modes, which instruct makeValid to only transfer common system fields.</p>
     */
    public static final String IN_PARAM = "IN";

    /**
     * Service parameter mode representing output fields, for use with {@link #makeValid}.
     *
     * <p>NOTE: When specifying to {@link #makeValid}, it's best to simply hardcode the string for brevity and imports.</p>
     *
     * <p>SCIPIO: 3.0.0: Added special *-SYS modes, which instruct makeValid to only transfer common system fields.</p>
     */
    public static final String OUT_PARAM = "OUT";

    /**
     * Service parameter mode representing input and output fields, for use with {@link #makeValid}.
     *
     * <p>NOTE: When specifying to {@link #makeValid}, it's best to simply hardcode the string for brevity and imports.</p>
     *
     * <p>SCIPIO: 3.0.0: Added special *-SYS modes, which instruct makeValid to only transfer common system fields.</p>
     */
    public static final String IN_OUT_PARAM = "INOUT";

    /**
     * Service parameter mode representing only the common input system fields {@link #IN_SYS_PARAMS}, for use with {@link #makeValid}.
     *
     * <p>NOTE: When specifying to {@link #makeValid}, it's best to simply hardcode the string for brevity and imports.</p>
     *
     * <p>SCIPIO: 3.0.0: Added special *-SYS modes, which instruct makeValid to only transfer common system fields.</p>
     */
    public static final String IN_SYS_PARAM = "IN-SYS";

    /**
     * Service parameter mode representing only the common output system fields {@link #OUT_SYS_PARAMS}, for use with {@link #makeValid}.
     *
     * <p>NOTE: When specifying to {@link #makeValid}, it's best to simply hardcode the string for brevity and imports.</p>
     *
     * <p>SCIPIO: 3.0.0: Added special *-SYS modes, which instruct makeValid to only transfer common system fields.</p>
     */
    public static final String OUT_SYS_PARAM = "OUT-SYS";

    /**
     * Service parameter representing only the common input and output system fields {@link #IN_OUT_SYS_PARAMS}, for use with {@link #makeValid}.
     *
     * <p>NOTE: When specifying to {@link #makeValid}, it's best to simply hardcode the string for brevity and imports.</p>
     *
     * <p>SCIPIO: 3.0.0: Added special *-SYS modes, which instruct makeValid to only transfer common system fields.</p>
     */
    public static final String IN_OUT_SYS_PARAM = "INOUT-SYS";

    /**
     * Maps service parameter mode to the corresponding I/O mode without suffix.
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static final Map<String, String> PARAM_MODE_IO_MAP = UtilMisc.constMap(
            IN_PARAM, IN_PARAM,
            OUT_PARAM, OUT_PARAM,
            IN_OUT_PARAM, IN_OUT_PARAM,
            IN_SYS_PARAM, IN_PARAM,
            OUT_SYS_PARAM, OUT_PARAM,
            IN_OUT_SYS_PARAM, IN_OUT_PARAM);

    public static final List<String> PARAM_MODES_IO = List.of(IN_PARAM, OUT_PARAM, IN_OUT_PARAM);
    public static final List<String> PARAM_MODES_SYS = List.of(IN_SYS_PARAM, OUT_SYS_PARAM, IN_OUT_SYS_PARAM);
    public static final List<String> PARAM_MODES = List.copyOf(UtilMisc.addAll(new LinkedHashSet<>(PARAM_MODES_IO), PARAM_MODES_SYS));

    public static final String LOCALE = "locale";
    public static final String TIMEZONE = "timeZone";
    public static final String USERLOGIN = "userLogin";
    public static final String LOGIN_USERNAME = "login.username";
    public static final String LOGIN_PASSWORD = "login.password";

    /**
     * Default service parameter input system fields, including login.username and login.password.
     *
     * <p>These are the parameters by default transferred when {@link #IN_SYS_PARAM} or {@link #IN_OUT_SYS_PARAM}
     * are passed as {@link #makeValid} service context mode.</p>
     *
     * <p>NOTE: "system" is not necessarily equivalent to the definition of "internal" for every service, and is preferable
     * for most client code service context operations.</p>
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static final List<String> IN_SYS_PARAMS = List.of(
            "locale",
            "timeZone",
            "userLogin",
            "login.username",
            "login.password");

    public static final List<String> IN_PW_PARAMS = List.of(
            "login.username",
            "login.password");

    /**
     * Default service input system parameters, including login.username and login.password.
     *
     * <p>NOTE: "system" is not necessarily equivalent to the definition of "internal" for every service, and is preferable
     * for most client code service context operations.</p>
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static final List<String> IN_SYSPW_PARAMS = IN_SYS_PARAMS;

    /**
     * Default service input system parameters, excluding login.username and login.password.
     *
     * <p>NOTE: "system" is not necessarily equivalent to the definition of "internal" for every service, and is preferable
     * for most client code service context operations.</p>
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static final List<String> IN_SYSNOPW_PARAMS = List.copyOf(IN_SYSPW_PARAMS.stream().filter(p -> !IN_PW_PARAMS.contains(p)).collect(Collectors.toList()));

    public static final String RESPONSE_MESSAGE = "responseMessage";
    public static final String RESPOND_SUCCESS = "success";
    public static final String RESPOND_ERROR = "error";
    public static final String RESPOND_FAIL = "fail";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String ERROR_MESSAGE_LIST = "errorMessageList";
    public static final String ERROR_MESSAGE_MAP = "errorMessageMap";
    public static final String SUCCESS_MESSAGE = "successMessage";
    public static final String SUCCESS_MESSAGE_LIST = "successMessageList";

    /**
     * Default service input system parameters.
     *
     * <p>These are the parameters by default transferred when {@link #OUT_SYS_PARAM} or {@link #IN_OUT_SYS_PARAM}
     * are passed as {@link #makeValid} service context mode.</p>
     *
     * <p>NOTE: "system" is not necessarily equivalent to the definition of "internal" for every service, and is preferable
     * for most client code service context operations.</p>
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static final List<String> OUT_SYS_PARAMS = List.of(
            RESPONSE_MESSAGE,
            ERROR_MESSAGE,
            ERROR_MESSAGE_LIST,
            ERROR_MESSAGE_MAP,
            SUCCESS_MESSAGE,
            SUCCESS_MESSAGE_LIST);

    /**
     * Default service input system parameters.
     *
     * <p>NOTE: "system" is not necessarily equivalent to the definition of "internal" for every service, and is preferable
     * for most client code service context operations.</p>
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static final List<String> IN_OUT_SYS_PARAMS = List.copyOf(UtilMisc.addAll(new ArrayList<>(IN_SYS_PARAMS), OUT_SYS_PARAMS));

    /**
     * Maps special service parameter modes that designate specific parameters to the corresponding fields for that mode.
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static final Map<String, List<String>> PARAM_MODE_PARAMS_MAP = UtilMisc.constMap(
            IN_SYS_PARAM, IN_SYS_PARAMS,
            OUT_SYS_PARAM, OUT_SYS_PARAMS,
            IN_OUT_SYS_PARAM, IN_OUT_SYS_PARAMS);

    /**
     * List of the common system response field keys returnable in service results.
     * @deprecated SCIPIO: 3.0.0: Use {@link #OUT_SYS_PARAMS}
     *
     * <p>SCIPIO: 2017-11-28: Added.</p>
     */
    @Deprecated
    public static final List<String> SYS_RESPONSE_FIELDS = OUT_SYS_PARAMS;

    /**
     * List of the common system response field keys returnable in service results, as a (Hash)Set.
     * @deprecated SCIPIO: 3.0.0: Use {@link #OUT_SYS_PARAMS}
     *
     * <p>SCIPIO: 2017-11-23: Added.</p>
     */
    @Deprecated
    public static final Set<String> SYS_RESPONSE_FIELDS_SET = UtilMisc.constSetCopy(OUT_SYS_PARAMS);

    // SCIPIO: Added 2017-09-13
    private static final int logParamLevel;
    static {
        Integer level = Debug.getLevelFromString(UtilProperties.getPropertyValue("service", "run.logParamLevel"));
        logParamLevel = (level != null) ? level : Debug.INFO;
    }

    @Deprecated
    public static final List<String> COMMON_INTERNAL_IN_FIELDS = IN_SYSNOPW_PARAMS;

    /**
     * Stack overflow limit for interfaceUpdate.
     * <p>SCIPIO: 2.1.0: Added for user-friendliness.</p>
     */
    private static final int IFC_MAXCALLS = 1000;

    private static final ThreadLocal<Integer> IFC_CALLS = new ThreadLocal<>();

    /** The name of this service */
    public String name;

    /** The location of the definition this service */
    public String definitionLocation;

    /** The description of this service */
    public String description;

    /** The name of the service engine */
    public String engineName;

    /** The namespace of this service */
    public String nameSpace;

    /** The namespace prefix of this service */
    public String nameSpacePrefix;

    /** The package name or location of this service */
    public String location;

    /** The method or function to invoke for this service */
    public String invoke;

    /**
     * The package name or location of the accessor (factory) class of this service.
     * <p>Defaults to {@link #location}.</p>
     * <p>SCIPIO: 2.1.0: Added.</p>
     */
    public String accessorLocation;

    /**
     * The method or function to invoke for the accessor (factory) class of this service.
     * <p>SCIPIO: 2.1.0: Added.</p>
     */
    public String accessorInvoke;

    /** The default Entity to use for auto-attributes */
    public String defaultEntityName;

    /** The loader which loaded this definition */
    public String fromLoader;

    /** Does this service require authorization */
    public boolean auth;

    /** Can this service be exported via RPC, RMI, SOAP, etc */
    public boolean export;

    /** Enable verbose debugging when calling this service */
    public boolean debug;

    /** Validate the context info for this service */
    public boolean validate;

    /** Create a transaction for this service (if one is not already in place...)? */
    public boolean useTransaction;

    /** Require a new transaction for this service */
    public boolean requireNewTransaction;

    /** Override the default transaction timeout, only works if we start the transaction */
    public int transactionTimeout;

    /** Sets the max number of times this service will retry when failed (persisted async only) */
    public int maxRetry = -1;

    /** Permission service name */
    public String permissionServiceName;

    /** Permission service main-action */
    public String permissionMainAction;

    /** Permission service resource-description */
    public String permissionResourceDesc;

    /** Semaphore setting (wait, fail, none) */
    public String semaphore;

    /** Semaphore wait time (in milliseconds) */
    public int semaphoreWait;

    /** Semaphore sleep time (in milliseconds) */
    public int semaphoreSleep;

    /** Require a new transaction for this service */
    public boolean hideResultInLog;

    /** Set of services this service implements */
    public Set<ModelServiceIface> implServices = new LinkedHashSet<>();

    /** Set of override parameters */
    public Set<ModelParam> overrideParameters = new LinkedHashSet<>();

    /** List of permission groups for service invocation */
    public List<ModelPermGroup> permissionGroups = new ArrayList<>(); // SCIPIO: switched to ArrayList

    /** List of email-notifications for this service */
    public List<ModelNotification> notifications = new ArrayList<>(); // SCIPIO: switched to ArrayList

    /** Internal Service Group */
    public GroupModel internalGroup = null;

    /**Deprecated information*/
    public String deprecatedUseInstead = null;
    public String deprecatedSince = null;
    public String deprecatedReason = null;

    /** Context Information, a Map of parameters used by the service, contains ModelParam objects */
    protected Map<String, ModelParam> contextInfo = new LinkedHashMap<>();

    /** Context Information, a List of parameters used by the service, contains ModelParam objects */
    protected List<ModelParam> contextParamList = new ArrayList<>(); // SCIPIO: switched to ArrayList

    /** Flag to say if we have pulled in our addition parameters from our implemented service(s) */
    protected boolean inheritedParameters = false;

    /**
     * Service metrics.
     */
    public Metrics metrics = null;

    /**
     * Defines custom service properties, which can be interpreted by the system or custom
     * code as needed.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    protected Map<String, Object> properties = Collections.emptyMap();

    /**
     * Saved relative definition location, as opposed to absolute.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    String relativeDefinitionLocation;

    /**
     * Logical log level for services.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    public enum LogLevel { // SCIPIO
        DEBUG,
        NORMAL,
        QUIET;
        
        public static LogLevel fromName(String name, LogLevel defaultValue) {
            if (UtilValidate.isEmpty(name)) {
                return defaultValue;
            }
            return LogLevel.valueOf(LogLevel.class, name.toUpperCase());
        }
        
        public boolean isDebug() {
            return this == DEBUG;
        }
        public boolean isQuiet() {
            return this == QUIET;
        }
    }
    
    /**
     * If true, avoids optional logging.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    LogLevel logLevel = LogLevel.NORMAL;

    /**
     * Log level for when this service is called from ECAs.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    LogLevel ecaLogLevel = LogLevel.NORMAL;

    /**
     * Filters out some trace logging based on dispatcher name.
     * <p>SCIPIO: 2.1.0: Added.</p>
     */
    Pattern logTraceExcludeDispatcherRegex;

    private transient List<ModelParam> typeConvertParamList; // SCIPIO

    /**
     * The last service this one overrides (which may override one before it).
     * This is now used for both debugging and to implement self interfaces.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    ModelService overriddenService; // SCIPIO: This is always null unless

    /**
     * Service priority for async and job services.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    Long priority;

    /**
     * Default job pool when service invoked as persistent job, when not overridden by caller.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    String jobPoolPersist;

    /**
     * Start delay, in milliseconds (thread sleep).
     * <p>SCIPIO: 2.1.0: Added start-delay service attribute.</p>
     */
    Integer startDelay;

    /**
     * Cached reflection info for StandardJavaEngine.
     * <p>SCIPIO: 2.1.0: Added.</p>
     */
    transient Object javaServiceReflectInfo;

    /**
     * Access modifier for publicly-reachable interfaces.
     * <p>SCIPIO: 2.x.x: Added.</p>
     */
    public static class Access implements Serializable {
        public static final Access PUBLIC = new Access("public");
        public static final Access INTERNAL = new Access("internal");

        private final String name;

        protected Access(String name) {
            this.name = name;
        }

        public static Access fromName(String name, Access defaultValue) throws IllegalArgumentException {
            if (UtilValidate.isEmpty(name)) {
                return defaultValue;
            } else if (PUBLIC.getName().equals(name)) {
                return PUBLIC;
            } else if (INTERNAL.getName().equals(name)) {
                return INTERNAL;
            } else {
                throw new IllegalArgumentException("Invalid service attribute access level name: " + name);
            }
        }

        public String getName() {
            return name;
        }

        public boolean isPublic() {
            return this == PUBLIC;
        }
        public boolean isInternal() {
            return this == INTERNAL;
        }
    }

    public ModelService() {}

    public ModelService(ModelService model) {
        this.name = model.name;
        this.semaphore = model.semaphore;
        this.fromLoader = model.fromLoader;
        this.debug = model.debug;
        this.semaphoreWait = model.semaphoreWait;
        this.semaphoreSleep = model.semaphoreSleep;
        this.contextInfo = model.contextInfo;
        this.definitionLocation = model.definitionLocation;
        this.description = model.description;
        this.engineName = model.engineName;
        this.nameSpace = model.nameSpace;
        this.nameSpacePrefix = model.nameSpacePrefix;
        this.location = model.location;
        this.invoke = model.invoke;
        this.defaultEntityName = model.defaultEntityName;
        this.auth = model.auth;
        this.export = model.export;
        this.validate = model.validate;
        this.useTransaction = model.useTransaction;
        this.requireNewTransaction = model.requireNewTransaction;
        if (this.requireNewTransaction && !this.useTransaction) {
            // requireNewTransaction implies that a transaction is used
            this.useTransaction = true;
        }
        this.transactionTimeout = model.transactionTimeout;
        this.maxRetry = model.maxRetry;
        this.permissionServiceName = model.permissionServiceName;
        this.permissionMainAction = model.permissionMainAction;
        this.permissionResourceDesc = model.permissionResourceDesc;
        this.implServices = model.implServices;
        this.overrideParameters = model.overrideParameters;
        this.inheritedParameters = model.inheritedParameters();
        this.internalGroup = model.internalGroup;
        this.hideResultInLog = model.hideResultInLog;
        this.metrics = model.metrics;
        List<ModelParam> modelParamList = model.getModelParamList();
        for (ModelParam param: modelParamList) {
            this.addParamClone(param);
        }
        // SCIPIO
        this.relativeDefinitionLocation = model.relativeDefinitionLocation;
        this.logLevel = model.logLevel;
        this.ecaLogLevel = model.ecaLogLevel;
        this.logTraceExcludeDispatcherRegex = model.logTraceExcludeDispatcherRegex;
        this.typeConvertParamList = model.typeConvertParamList;
        this.priority = model.priority;
        this.jobPoolPersist = model.jobPoolPersist;
        this.startDelay = model.startDelay;
        this.accessorLocation = model.accessorLocation;
        this.accessorInvoke = model.accessorInvoke;
        this.overriddenService = model.overriddenService;
    }

    @Override
    public Object get(Object name) {
        Field field = MODEL_SERVICE_FIELD_MAP.get(name.toString());
        if (field != null) {
            try {
                return field.get(this);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    private final class ModelServiceMapEntry implements Map.Entry<String, Object> {
        private final Field field;

        protected ModelServiceMapEntry(Field field) {
            this.field = field;
        }

        public String getKey() {
            return field.getName();
        }

        public Object getValue() {
            try {
                return field.get(ModelService.this);
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        public Object setValue(Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            return field.hashCode() ^ System.identityHashCode(ModelService.this);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ModelServiceMapEntry)) {
                return false;
            }
            ModelServiceMapEntry other = (ModelServiceMapEntry) o;
            return field.equals(other.field) && ModelService.this == other.getModelService();
        }

        private ModelService getModelService() {
            return ModelService.this;
        }
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return new AbstractSet<Map.Entry<String, Object>>() {
            @Override
            public int size() {
                return MODEL_SERVICE_FIELDS.length;
            }

            @Override
            public Iterator<Map.Entry<String, Object>> iterator() {
                return new Iterator<Map.Entry<String, Object>>() {
                    private int i = 0;

                    public boolean hasNext() {
                        return i < MODEL_SERVICE_FIELDS.length;
                    }

                    public Map.Entry<String, Object> next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        return new ModelServiceMapEntry(MODEL_SERVICE_FIELDS[i++]);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    @Override
    public Object put(String o1, Object o2) {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(name).append("::");
        buf.append(definitionLocation).append("::");
        buf.append(description).append("::");
        buf.append(engineName).append("::");
        buf.append(nameSpace).append("::");
        buf.append(nameSpacePrefix).append("::");
        buf.append(location).append("::");
        buf.append(invoke).append("::");
        buf.append(defaultEntityName).append("::");
        buf.append(auth).append("::");
        buf.append(export).append("::");
        buf.append(validate).append("::");
        buf.append(useTransaction).append("::");
        buf.append(requireNewTransaction).append("::");
        buf.append(transactionTimeout).append("::");
        buf.append(implServices).append("::");
        buf.append(overrideParameters).append("::");
        buf.append(contextInfo).append("::");
        buf.append(contextParamList).append("::");
        buf.append(inheritedParameters).append("::");
        buf.append(hideResultInLog).append("::");
        buf.append(accessorLocation).append("::");
        buf.append(accessorInvoke).append("::");
        return buf.toString();
    }

    public String debugInfo() {
        if (debug || Debug.verboseOn()) {
            return " [" + this.toString() + "]";
        }
        return "";
    }

    public String getName() {
        return name;
    }

    public String getDefinitionLocation() {
        return definitionLocation;
    }

    public String getDescription() {
        return description;
    }

    public String getEngineName() {
        return engineName;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    public String getNameSpacePrefix() {
        return nameSpacePrefix;
    }

    public String getLocation() {
        return location;
    }

    public String getInvoke() {
        return invoke;
    }

    public String getAccessorLocation() {
        return accessorLocation;
    }

    public String getAccessorInvoke() {
        return accessorInvoke;
    }

    public String getDefaultEntityName() {
        return defaultEntityName;
    }

    public String getFromLoader() {
        return fromLoader;
    }

    public boolean isAuth() {
        return auth;
    }

    public boolean isExport() {
        return export;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isValidate() {
        return validate;
    }

    public boolean isUseTransaction() {
        return useTransaction;
    }

    public boolean isRequireNewTransaction() {
        return requireNewTransaction;
    }

    public int getTransactionTimeout() {
        return transactionTimeout;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public String getPermissionServiceName() {
        return permissionServiceName;
    }

    public String getPermissionMainAction() {
        return permissionMainAction;
    }

    public String getPermissionResourceDesc() {
        return permissionResourceDesc;
    }

    public String getSemaphore() {
        return semaphore;
    }

    public int getSemaphoreWait() {
        return semaphoreWait;
    }

    public int getSemaphoreSleep() {
        return semaphoreSleep;
    }

    public boolean isHideResultInLog() {
        return hideResultInLog;
    }

    public Set<ModelServiceIface> getImplServices() {
        return implServices;
    }

    public Set<ModelParam> getOverrideParameters() {
        return overrideParameters;
    }

    public List<ModelPermGroup> getPermissionGroups() {
        return permissionGroups;
    }

    public List<ModelNotification> getNotifications() {
        return notifications;
    }

    public GroupModel getInternalGroup() {
        return internalGroup;
    }

    public String getDeprecatedUseInstead() {
        return deprecatedUseInstead;
    }

    public String getDeprecatedSince() {
        return deprecatedSince;
    }

    public String getDeprecatedReason() {
        return deprecatedReason;
    }

    public Map<String, ModelParam> getContextInfo() {
        return contextInfo;
    }

    public List<ModelParam> getContextParamList() {
        return contextParamList;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Test if we have already inherited our interface parameters
     * @return boolean
     */
    public synchronized boolean inheritedParameters() {
        return this.inheritedParameters;
    }

    /**
     * Gets the ModelParam by name
     * @param name The name of the parameter to get
     * @return ModelParam object with the specified name
     */
    public ModelParam getParam(String name) {
        return contextInfo.get(name);
    }

    public boolean hasParam(String name) { return getParam(name) != null; }

    /**
     * Adds a parameter definition to this service; puts on list in order added
     * then sorts by order if specified.
     */
    public void addParam(ModelParam param) {
        if (param != null) {
            contextInfo.put(param.name, param);
            contextParamList.add(param);
        }
    }

    /* DEJ20060125 This is private but not used locally, so just commenting it out for now... may remove later
    private void copyParams(Collection params) {
        if (params != null) {
            Iterator i = params.iterator();
            while (i.hasNext()) {
                ModelParam param = (ModelParam) i.next();
                addParam(param);
            }
        }
    }
    */

    /**
     * Adds a clone of a parameter definition to this service
     */
    public void addParamClone(ModelParam param) {
        if (param != null) {
            ModelParam newParam = new ModelParam(param);
            addParam(newParam);
        }
    }

    public Set<String> getAllParamNames() {
        Set<String> nameList = new TreeSet<>();
        for (ModelParam p: this.contextParamList) {
            nameList.add(p.name);
        }
        return nameList;
    }

    public Set<String> getInParamNames() {
        Set<String> nameList = new TreeSet<>();
        for (ModelParam p: this.contextParamList) {
            // don't include OUT parameters in this list, only IN and INOUT
            if (p.isIn()) {
                nameList.add(p.name);
            }
        }
        return nameList;
    }

    // only returns number of defined parameters (not internal)
    public int getDefinedInCount() {
        int count = 0;

        for (ModelParam p: this.contextParamList) {
            // don't include OUT parameters in this list, only IN and INOUT
            if (p.isIn() && !p.internal) {
                count++;
            }
        }

        return count;
    }

    public Set<String> getOutParamNames() {
        Set<String> nameList = new TreeSet<>();
        for (ModelParam p: this.contextParamList) {
            // don't include IN parameters in this list, only OUT and INOUT
            if (p.isOut()) {
                nameList.add(p.name);
            }
        }
        return nameList;
    }

    // only returns number of defined parameters (not internal)
    public int getDefinedOutCount() {
        int count = 0;

        for (ModelParam p: this.contextParamList) {
            // don't include IN parameters in this list, only OUT and INOUT
            if (p.isOut() && !p.internal) {
                count++;
            }
        }

        return count;
    }

    public void updateDefaultValues(Map<String, Object> context, String mode) {
        List<ModelParam> params = this.getModelParamList();
        for (ModelParam param: params) {
            if (IN_OUT_PARAM.equals(param.mode) || mode.equals(param.mode)) {
                Object defaultValueObj = param.getDefaultValue();
                if (defaultValueObj != null && context.get(param.name) == null) {
                    context.put(param.name, defaultValueObj);
                    // SCIPIO: 2017-09-13: This message is extremely verbose and counterproductive as info level;
                    // it makes developers avoid the default-value attribute altogether.
                    // so, only log if debug flag or verbose are enabled (added conditions). verbose is configurable.
                    //Debug.logInfo(...);
                    if ((Debug.isOn(logParamLevel) && !this.isQuiet()) || this.debug) {
                        Debug.logInfo("Set default value [" + defaultValueObj + "] for parameter [" + param.name + "]", module);
                    }
                }
            }
        }
    }

    /**
     * Validates a Map against the IN or OUT parameter information
     * @param context the context
     * @param mode Test either mode IN or mode OUT
     * @param locale the actual locale to use
     * @param log Allow logging (SCIPIO)
     */
    public void validate(Map<String, Object> context, String mode, Locale locale, boolean log) throws ServiceValidationException {
        Map<String, String> requiredInfo = new HashMap<>();
        Map<String, String> optionalInfo = new HashMap<>();

        if (log && Debug.verboseOn()) Debug.logVerbose("[ModelService.validate] : {" + this.name + "} : Validating context - " + context, module);

        // do not validate results with errors
        if (mode.equals(OUT_PARAM) && context != null && context.containsKey(RESPONSE_MESSAGE)) {
            if (RESPOND_ERROR.equals(context.get(RESPONSE_MESSAGE)) || RESPOND_FAIL.equals(context.get(RESPONSE_MESSAGE))) {
                if (log && Debug.verboseOn()) Debug.logVerbose("[ModelService.validate] : {" + this.name + "} : response was an error, not validating.", module);
                return;
            }
        }

        // get the info values
        for (ModelParam modelParam: this.contextParamList) {
            if (IN_OUT_PARAM.equals(modelParam.mode) || mode.equals(modelParam.mode)) {
                if (modelParam.optional) {
                    optionalInfo.put(modelParam.name, modelParam.type);
                } else {
                    requiredInfo.put(modelParam.name, modelParam.type);
                }
            }
        }

        // get the test values
        Map<String, Object> requiredTest = new HashMap<>();
        Map<String, Object> optionalTest = new HashMap<>();

        if (context == null) {
            context = new HashMap<>();
        }
        requiredTest.putAll(context);

        List<String> requiredButNull = new ArrayList<>(); // SCIPIO: switched to ArrayList
        List<String> keyList = new ArrayList<>(); // SCIPIO: switched to ArrayList
        keyList.addAll(requiredTest.keySet());
        for (String key: keyList) {
            Object value = requiredTest.get(key);

            if (!requiredInfo.containsKey(key)) {
                requiredTest.remove(key);
                optionalTest.put(key, value);
            } else if (value == null) {
                requiredButNull.add(key);
            }
        }

        // check for requiredButNull fields and return an error since null values are not allowed for required fields
        if (requiredButNull.size() > 0) {
            List<String> missingMsg = new ArrayList<>(); // SCIPIO: switched to ArrayList
            for (String missingKey: requiredButNull) {
                String message = this.getParam(missingKey).getPrimaryFailMessage(locale);
                if (message == null) {
                    String errMsg = UtilProperties.getMessage(ServiceUtil.getResource(), "ModelService.following_required_parameter_missing", locale);
                    message = errMsg + " [" + this.name + "." + missingKey + "]";
                }
                missingMsg.add(message);
            }
            throw new ServiceValidationException(missingMsg, this, requiredButNull, null, mode);
        }

        if (Debug.verboseOn()) {
            StringBuilder requiredNames = new StringBuilder();

            for (String key: requiredInfo.keySet()) {
                if (requiredNames.length() > 0) {
                    requiredNames.append(", ");
                }
                requiredNames.append(key);
            }
            if (Debug.verboseOn()) Debug.logVerbose("[ModelService.validate] : required fields - " + requiredNames, module);

            if (Debug.verboseOn()) Debug.logVerbose("[ModelService.validate] : {" + name + "} : (" + mode + ") Required - " +
                requiredTest.size() + " / " + requiredInfo.size(), module);
            if (Debug.verboseOn()) Debug.logVerbose("[ModelService.validate] : {" + name + "} : (" + mode + ") Optional - " +
                optionalTest.size() + " / " + optionalInfo.size(), module);
        }

        try {
            validate(requiredInfo, requiredTest, true, this, mode, locale);
            validate(optionalInfo, optionalTest, false, this, mode, locale);
        } catch (ServiceValidationException e) {
            if (log) {
                Debug.logError("[ModelService.validate] : {" + name + "} : (" + mode + ") Required test error: " + e.toString(), module);
            }
            throw e;
        }

        // required and type validation complete, do allow-html validation
        if (IN_PARAM.equals(mode)) {
            List<String> errorMessageList = new ArrayList<>(); // SCIPIO: switched to ArrayList
            for (ModelParam modelParam : this.contextInfo.values()) {
                // the param is a String, allow-html is not any, and we are looking at an IN parameter during input parameter validation
                if (context.get(modelParam.name) != null && ("String".equals(modelParam.type) || "java.lang.String".equals(modelParam.type))
                        && !"any".equals(modelParam.allowHtml) && (IN_OUT_PARAM.equals(modelParam.mode) || IN_PARAM.equals(modelParam.mode))) {
                    String value = (String) context.get(modelParam.name);
                    UtilCodec.checkStringForHtmlStrictNone(modelParam.name, value, errorMessageList);
                }
            }
            if (errorMessageList.size() > 0) {
                throw new ServiceValidationException(errorMessageList, this, mode);
            }
        }
    }

    /**
     * Validates a Map against the IN or OUT parameter information
     * @param context the context
     * @param mode Test either mode IN or mode OUT
     * @param locale the actual locale to use
     */
    public void validate(Map<String, Object> context, String mode, Locale locale) throws ServiceValidationException {
        validate(context, mode, locale, true);
    }

    /**
     * Check a Map against the IN parameter information, uses the validate() method for that
     * Always called with only IN_PARAM, so to be called before the service is called with the passed context
     * @param context the passed context
     * @param locale the actual locale to use
     * @return boolean True is the service called with these IN_PARAM is valid
     */
    public boolean isValid(Map<String, Object> context, Locale locale) {
        try {
            validate(context, IN_PARAM, locale);
        } catch (ServiceValidationException e) {
            return false;
        }
        return true;
    }

    /**
     * Validates a map of name, object types to a map of name, objects
     * @param info The map of name, object types
     * @param test The map to test its value types.
     * @param reverse Test the maps in reverse.
     */
    public static void validate(Map<String, String> info, Map<String, ?> test, boolean reverse, ModelService model, String mode, Locale locale) throws ServiceValidationException {
        if (info == null || test == null) {
            throw new ServiceValidationException("Cannot validate NULL maps", model);
        }

        // * Validate keys first
        Set<String> testSet = test.keySet();
        Set<String> keySet = info.keySet();

        // Quick check for sizes
        if (info.size() == 0 && test.size() == 0) {
            return;
        }
        // This is to see if the test set contains all from the info set (reverse)
        if (reverse && !testSet.containsAll(keySet)) {
            Set<String> missing = new TreeSet<>(keySet);

            missing.removeAll(testSet);
            List<String> missingMsgs = new ArrayList<>(missing.size()); // SCIPIO: switched to ArrayList
            for (String key: missing) {
                String msg = model.getParam(key).getPrimaryFailMessage(locale);
                if (msg == null) {
                    String errMsg = UtilProperties.getMessage(ServiceUtil.getResource(), "ModelService.following_required_parameter_missing", locale) ;
                    msg = errMsg + " [" + mode + "] [" + model.name + "." + key + "]";
                }
                missingMsgs.add(msg);
            }

            List<String> missingCopy = new ArrayList<>(missing); // SCIPIO: switched to ArrayList
            //missingCopy.addAll(missing);
            throw new ServiceValidationException(missingMsgs, model, missingCopy, null, mode);
        }

        // This is to see if the info set contains all from the test set
        if (!keySet.containsAll(testSet)) {
            Set<String> extra = new TreeSet<>(testSet);

            extra.removeAll(keySet);
            List<String> extraMsgs = new ArrayList<>(extra.size()); // SCIPIO: switched to ArrayList
            for (String key: extra) {
                ModelParam param = model.getParam(key);
                String msg = null;
                if (param != null) {
                    msg = param.getPrimaryFailMessage(locale);
                }
                if (msg == null) {
                    msg = "Unknown parameter found: [" + model.name + "." + key + "]";
                }
                extraMsgs.add(msg);
            }

            List<String> extraCopy = new ArrayList<>(extra); // SCIPIO: switched to ArrayList
            //extraCopy.addAll(extra);
            throw new ServiceValidationException(extraMsgs, model, null, extraCopy, mode);
        }

        // * Validate types next
        List<String> typeFailMsgs = new ArrayList<>(); // SCIPIO: switched to ArrayList
        for (String key: testSet) {
            ModelParam param = model.getParam(key);

            Object testObject = test.get(key);
            String infoType = info.get(key);

            if (UtilValidate.isNotEmpty(param.validators)) {
                for (ModelParam.ModelParamValidator val: param.validators) {
                    if (UtilValidate.isNotEmpty(val.getMethodName())) {
                        try {
                            if (!typeValidate(val, testObject)) {
                                String msg = val.getFailMessage(locale);
                                if (msg == null) {
                                    msg = "The following parameter failed validation: [" + model.name + "." + key + "]";
                                }
                                typeFailMsgs.add(msg);
                            }
                        } catch (GeneralException e) {
                            Debug.logError(e, module);
                            String msg = param.getPrimaryFailMessage(locale);
                            if (msg == null) {
                                msg = "The following parameter failed validation: [" + model.name + "." + key + "]";
                            }
                            typeFailMsgs.add(msg);
                        }
                    } else {
                        if (!ObjectType.instanceOf(testObject, infoType, null)) {
                            String msg = val.getFailMessage(locale);
                            if (msg == null) {
                                msg = "The following parameter failed validation: [" + model.name + "." + key + "]";
                            }
                            typeFailMsgs.add(msg);
                        }
                    }
                }
            } else {
                if (!ObjectType.instanceOf(testObject, infoType, null)) {
                    String testType = testObject == null ? "null" : testObject.getClass().getName();
                    String msg = "Type check failed for field [" + model.name + "." + key + "]; expected type is [" + infoType + "]; actual type is [" + testType + "]";
                    typeFailMsgs.add(msg);
                }
            }
        }

        if (typeFailMsgs.size() > 0) {
            throw new ServiceValidationException(typeFailMsgs, model, mode);
        }
    }

    public static boolean typeValidate(ModelParam.ModelParamValidator vali, Object testValue) throws GeneralException {
        // find the validator class
        Class<?> validatorClass = null;
        try {
            validatorClass = ObjectType.loadClass(vali.getClassName());
        } catch (ClassNotFoundException e) {
            Debug.logWarning(e, module);
        }

        if (validatorClass == null) {
            throw new GeneralException("Unable to load validation class [" + vali.getClassName() + "]");
        }

        boolean foundObjectParam = true;

        Method validatorMethod = null;
        try {
            // try object type first
            validatorMethod = validatorClass.getMethod(vali.getMethodName(), Object.class);
        } catch (NoSuchMethodException e) {
            foundObjectParam = false;
            // next try string type
            try {
                validatorMethod = validatorClass.getMethod(vali.getMethodName(), String.class);
            } catch (NoSuchMethodException e2) {
                Debug.logWarning(e2, module);
            }
        }

        if (validatorMethod == null) {
            throw new GeneralException("Unable to find validation method [" + vali.getMethodName() + "] in class [" + vali.getClassName() + "]");
        }

        Object param;
        if (!foundObjectParam) {
            // convert to string
            String converted;
            try {
                converted = (String) ObjectType.simpleTypeConvert(testValue, "String", null, null);
            } catch (GeneralException e) {
                throw new GeneralException("Unable to convert parameter to String");
            }
            param = converted;
        } else {
            // use plain object
            param = testValue;
        }

        // run the validator
        Boolean resultBool;
        try {
            resultBool = (Boolean) validatorMethod.invoke(null, param);
        } catch (ClassCastException e) {
            throw new GeneralException("Validation method [" + vali.getMethodName() + "] in class [" + vali.getClassName() + "] did not return expected Boolean");
        } catch (Exception e) {
            throw new GeneralException("Unable to run validation method [" + vali.getMethodName() + "] in class [" + vali.getClassName() + "]");
        }

        return resultBool;
    }

    /**
     * Gets the parameter names of the specified mode (IN/OUT/INOUT). The
     * parameters will be returned in the order specified in the file.
     * Note: IN and OUT will also contains INOUT parameters.
     * @param mode The mode (IN/OUT/INOUT)
     * @param optional True if to include optional parameters
     * @param internal True to include internal parameters
     * @return List of parameter names
     */
    public List<String> getParameterNames(String mode, boolean optional, boolean internal) {
        List<String> names = new ArrayList<>(contextParamList.size()); // SCIPIO: switched to ArrayList

        if (!IN_PARAM.equals(mode) && !OUT_PARAM.equals(mode) && !IN_OUT_PARAM.equals(mode)) {
            return names;
        }
        if (contextInfo.size() == 0) {
            return names;
        }
        for (ModelParam param: contextParamList) {
            if (param.mode.equals(IN_OUT_PARAM) || param.mode.equals(mode)) {
                if (optional || !param.optional) {
                    if (internal || !param.internal) {
                        names.add(param.name);
                    }
                }
            }
        }
        return names;
    }

    public List<String> getParameterNames(String mode, boolean optional) {
        return this.getParameterNames(mode, optional, true);
    }

    /**
     * Creates a new Map based from an existing map with just valid parameters.
     * Tries to convert parameters to required type.
     *
     * <p>SCIPIO: 3.0.0: Reimplemented with {@link MakeValidOptions}; reordered source/mode overload to match DispatchContext.makeValidContext.</p>
     *
     * @param mode    The mode which to build the new map, one of {@link #PARAM_MODES}
     * @param source  The source map
     * @param options The options
     */
    public Map<String, Object> makeValid(String mode, Map<String, ?> source, MakeValidOptions options) {
        if (options == null) {
            options = MakeValidOptions.DEFAULT;
        }
        Map<String, Object> target = options.targetContext();
        if (target == null) {
            target = new HashMap<>();
        }
        if (source == null) {
            return target;
        }
        if (contextInfo.size() == 0) {
            return target;
        }

        Boolean includeInternal = options.includeInternal();
        if (includeInternal == null) {
            includeInternal = true;
        }
        boolean includeStandard = true;
        List<Object> errorMessages = options.errorMessages();
        TimeZone timeZone = options.timeZone();
        Locale locale = options.locale();
        String namePrefix = options.namePrefix();
        String toNamePrefix = options.toNamePrefix();
        if (locale == null) {
            locale = getLocale(source, errorMessages); // SCIPIO: Refactored
        }
        if (timeZone == null) {
            timeZone = getTimeZone(source, locale, errorMessages); // SCIPIO: Refactored
        }

        List<ModelParam> targetParams = getParamModeModelParamList(mode);
        if (targetParams == null) {
            targetParams = getContextParamList();
        }
        if (PARAM_MODES_SYS.contains(mode)) {
            mode = PARAM_MODE_IO_MAP.get(mode);
        } else if (!PARAM_MODES_IO.contains(mode)) {
            // SCIPIO: 3.0.0: Now throws exception here because we have a strict set of allowed values for mode
            //  (anythng else should never happen; it simply swallows errors dandgerously) and fixes inconsistency with DispatchContext
            //return target;
            throw new IllegalArgumentException("Invalid makeValid service parameters mode: " + mode + " (supported: " + ModelService.PARAM_MODES + ")");
        }

        for (ModelParam param : targetParams) {
            if ((param.mode.equals(mode) || param.mode.equals(IN_OUT_PARAM)) &&
                    ((param.internal && includeInternal) || (!param.internal && includeStandard))) {
                // SCIPIO: 3.0.0: Added src/dst prefix
                String srcKey = StringUtil.prefixFieldNameCamelCase(param.name, namePrefix);
                String dstKey = StringUtil.prefixFieldNameCamelCase(param.name, toNamePrefix);

                if (source.containsKey(srcKey)) {
                    Object value = source.get(srcKey);
                    try {
                        // no need to fail on type conversion; the validator will catch this
                        value = ObjectType.simpleTypeConvert(value, param.type, null, timeZone, locale, false);
                    } catch (GeneralException e) {
                        String errMsg = "Type conversion of field [" + srcKey + "] to type [" + param.type + "] failed for value \"" + value + "\": " + e;
                        Debug.logWarning("[ModelService.makeValid] : " + errMsg, module);
                        if (errorMessages != null) {
                            errorMessages.add(errMsg);
                        }
                    }
                    target.put(dstKey, value);
                } else if (UtilValidate.isNotEmpty(param.stringMapPrefix)) {
                    // internal map of strings
                    Map<String, Object> paramMap = this.makePrefixMap(source, param, namePrefix);
                    if (UtilValidate.isNotEmpty(paramMap)) {
                        target.put(dstKey, paramMap);
                    }
                } else if (UtilValidate.isNotEmpty(param.stringListSuffix)) {
                    // internal list of strings
                    List<Object> paramList = this.makeSuffixList(source, param);
                    if (UtilValidate.isNotEmpty(paramList)) {
                        target.put(dstKey, paramList);
                    }
                }
            }
        }
        return target;
    }

    /**
     * Creates a new Map based from an existing map with just valid parameters.
     * Tries to convert parameters to required type.
     *
     * <p>SCIPIO: 3.0.0: Added consistent-parameter overload to match DispatchContext.makeValidContext.</p>
     *
     * @param mode The mode which to build the new map, one of {@link #PARAM_MODES}
     * @param source The source map
     */
    public Map<String, Object> makeValid(String mode, Map<String, ?> source) {
        return makeValid(mode, source, null);
    }

    /**
     * Creates a new Map based from an existing map with just valid parameters.
     * Tries to convert parameters to required type.
     *
     * <p>SCIPIO: Prefer {@link #makeValid(String, Map)}.</p>
     *
     * @param source The source map
     * @param mode The mode which to build the new map
     */
    public Map<String, Object> makeValid(Map<String, ?> source, String mode) {
        return makeValid(mode, source, null);
    }

    /**
     * Creates a new Map based from an existing map with just valid parameters.
     * Tries to convert parameters to required type.
     *
     * <p>SCIPIO: Prefer {@link #makeValid(String, Map, MakeValidOptions)}.</p>
     *
     * @param source The source map
     * @param mode The mode which to build the new map
     * @param includeInternal When false will exclude internal fields
     */
    public Map<String, Object> makeValid(Map<String, ?> source, String mode, boolean includeInternal, List<Object> errorMessages) {
        return makeValid(source, mode, includeInternal, errorMessages, null);
    }

    /**
     * Creates a new Map based from an existing map with just valid parameters.
     * Tries to convert parameters to required type.
     *
     * <p>SCIPIO: Prefer {@link #makeValid(String, Map, MakeValidOptions)}.</p>
     *
     * @param source The source map
     * @param mode The mode which to build the new map
     * @param includeInternal When false will exclude internal fields
     * @param locale Locale to use to do some type conversion
     */
    public Map<String, Object> makeValid(Map<String, ?> source, String mode, boolean includeInternal, List<Object> errorMessages, Locale locale) {
        return makeValid(source, mode, includeInternal, errorMessages, null, locale);
    }

    /**
     * Creates a new Map based from an existing map with just valid parameters.
     * Tries to convert parameters to required type.
     *
     * <p>SCIPIO: Prefer {@link #makeValid(String, Map, MakeValidOptions)}.</p>
     *
     * @param source The source map
     * @param mode The mode which to build the new map
     * @param includeInternal When false will exclude internal fields
     * @param errorMessages the list of error messages
     * @param timeZone TimeZone to use to do some type conversion
     * @param locale Locale to use to do some type conversion
     */
    public Map<String, Object> makeValid(Map<String, ?> source, String mode, boolean includeInternal, List<Object> errorMessages, TimeZone timeZone, Locale locale) {
        return makeValid(mode, source, new MakeValidOptions(includeInternal, errorMessages, timeZone, locale));
    }

    private Locale getLocale(Map<String, ?> source, List<? super String> errorMessages) { // SCIPIO: Refactored from makeValid
        Locale locale = null;
        // if statement here to avoid warning messages for Entity ECA service input validation, even though less efficient that doing a straight get
        if (source.containsKey("locale")) {
            // SCIPIO: 2018-06-27: This should accept a string, so apply simpleTypeConvert (this uses UtilMisc.parseLocale)
            // NOTE: if includeInternal, the convert is run a second time further below; but if we are taking strings,
            // performance is not the concern anyway.
            //locale = (Locale) source.get("locale");
            Object value = source.get("locale");
            if (value instanceof Locale) {
                locale = (Locale) value;
            } else if (value != null) {
                try {
                    locale = (Locale) ObjectType.simpleTypeConvert(value, Locale.class.getName(), null, null, null, true);
                } catch (GeneralException e) {
                    // SCIPIO: NOTE: this message may be duplicated below in some cases when includeInternal==true,
                    // but we must always warn with special message here because this also affects the other fields
                    String errMsg = "Type conversion of special field [locale] to type [" + Locale.class.getName()
                            + "] failed for value \"" + value + "\" - other fields will use a default locale for conversion: " + e.toString();
                    Debug.logWarning("[ModelService.makeValid] : " + errMsg, module);
                    if (errorMessages != null) {
                        errorMessages.add(errMsg);
                    }
                }
            }
        }
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return locale;
    }
    
    private TimeZone getTimeZone(Map<String, ?> source, Locale locale, List<? super String> errorMessages) { // SCIPIO: Refactored from makeValid
        TimeZone timeZone = null;
        // if statement here to avoid warning messages for Entity ECA service input validation, even though less efficient that doing a straight get
        if (source.containsKey("timeZone")) {
            // SCIPIO: 2018-06-27: This should accept a string, so apply simpleTypeConvert (same as locale above)
            //timeZone = (TimeZone) source.get("timeZone");
            Object value = source.get("timeZone");
            if (value instanceof TimeZone) {
                timeZone = (TimeZone) value;
            } else if (value != null) {
                try {
                    timeZone = (TimeZone) ObjectType.simpleTypeConvert(value, TimeZone.class.getName(), null, null, locale, true);
                } catch (GeneralException e) {
                    String errMsg = "Type conversion of special field [timeZone] to type [" + TimeZone.class.getName()
                            + "] failed for value \"" + value + "\" - other fields will use a default TimeZone for conversion: " + e.toString();
                    Debug.logWarning("[ModelService.makeValid] : " + errMsg, module);
                    if (errorMessages != null) {
                        errorMessages.add(errMsg);
                    }
                }
            }
        }
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        return timeZone;
    }
    
    /**
     * SCIPIO: Performs auto type conversions for fields marked type-convert="true", in-place in the context.
     */
    public void applyTypeConvert(Map<String, Object> context, String mode, Locale locale, TimeZone timeZone, List<? super String> errorMessages) {
        for(ModelParam param : getTypeConvertParamList()) {
            if (param.mode.equals(IN_OUT_PARAM) || param.mode.equals(mode)) {
                String key = param.name;
                Object value = context.get(key);
                if (value != null) {
                    if (locale == null) {
                        locale = getLocale(context, errorMessages); // SCIPIO: Refactored
                    }
    
                    if (timeZone == null) {
                        timeZone = getTimeZone(context, locale, errorMessages); // SCIPIO: Refactored
                    }
                    try {
                        // no need to fail on type conversion; the validator will catch this
                        value = ObjectType.simpleTypeConvert(value, param.type, null, timeZone, locale, false);
                        context.put(key, value);
                    } catch (GeneralException e) {
                        String errMsg = "Type conversion of field [" + key + "] to type [" + param.type + "] failed for value \"" + value + "\": " + e.toString();
                        Debug.logWarning("[ModelService.makeValid] : " + errMsg, module);
                        if (errorMessages != null) {
                            errorMessages.add(errMsg);
                        }
                    }
                }
            }
        }
    }

    private Map<String, Object> makePrefixMap(Map<String, ?> source, ModelParam param, String namePrefix) {
        Map<String, Object> paramMap = new LinkedHashMap<>();
        String stringMapPrefix = StringUtil.prefixFieldNameCamelCase(param.stringMapPrefix, namePrefix);
        for (Map.Entry<String, ?> entry: source.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(stringMapPrefix)) {
                // SCIPIO: 3.0.0: Fixed - inappropriate and slow
                //key=key.replace(param.stringMapPrefix,"");
                key = key.substring(stringMapPrefix.length());
                paramMap.put(key, entry.getValue());
            }
        }
        return paramMap;
    }

    private List<Object> makeSuffixList(Map<String, ?> source, ModelParam param) {
        List<Object> paramList = new ArrayList<>(source.size()); // SCIPIO: switched to ArrayList
        for (Map.Entry<String, ?> entry: source.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(param.stringListSuffix)) {
                paramList.add(entry.getValue());
            }
        }
        return paramList;
    }

    public boolean containsPermissions() {
        return (UtilValidate.isNotEmpty(this.permissionGroups));
    }

    /**
     * Evaluates permission-service for this service.
     * @param dctx DispatchContext from the invoked service
     * @param context Map containing userLogin and context information
     * @return result of permission service invocation
     */
    public Map<String, Object> evalPermission(DispatchContext dctx, Map<String, ?> context) {
        if (UtilValidate.isNotEmpty(this.permissionServiceName)) {
            ModelService thisService;
            ModelService permission;
            try {
                thisService = dctx.getModelService(this.name);
                permission = dctx.getModelService(this.permissionServiceName);
            } catch (GenericServiceException e) {
                Debug.logError(e, "Failed to get ModelService: " + e.toString(), module);
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("hasPermission", Boolean.FALSE);
                result.put("failMessage", e.getMessage());
                return result;
            }
            Map<String, Object> ctx = permission.makeValid(context, IN_PARAM);
            if (UtilValidate.isNotEmpty(this.permissionMainAction)) {
                ctx.put("mainAction", this.permissionMainAction);
            }
            if (UtilValidate.isNotEmpty(this.permissionResourceDesc)) {
                ctx.put("resourceDescription", this.permissionResourceDesc);
            }
            ctx.put("resourceDescription", thisService.name);

            LocalDispatcher dispatcher = dctx.getDispatcher();
            Map<String, Object> resp;
            try {
                resp = dispatcher.runSync(permission.name, ctx, 300, true);
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("hasPermission", Boolean.FALSE);
                result.put("failMessage", e.getMessage());
                return result;
            }
            if (ServiceUtil.isError(resp) || ServiceUtil.isFailure(resp)) {
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("hasPermission", Boolean.FALSE);
                String failMessage = (String) resp.get("failMessage");
                if (UtilValidate.isEmpty(failMessage)) {
                    failMessage = ServiceUtil.getErrorMessage(resp);
                }
                result.put("failMessage", failMessage);
                return result;
            }
            return resp;
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("hasPermission", Boolean.FALSE);
        result.put("failMessage", "No ModelService found; no service name specified!");
        return result;
    }

    /**
     * Evaluates notifications
     */
    public void evalNotifications(DispatchContext dctx, Map<String, ?> context, Map<String, Object> result) {
        for (ModelNotification notify: this.notifications) {
            notify.callNotify(dctx, this, context, result);
        }
    }

    /**
     * Evaluates permissions for a service.
     * @param dctx DispatchContext from the invoked service
     * @param context Map containing userLogin information
     * @return true if all permissions evaluate true.
     */
    public boolean evalPermissions(DispatchContext dctx, Map<String, ?> context) {
        // old permission checking
        if (this.containsPermissions()) {
            for (ModelPermGroup group: this.permissionGroups) {
                if (!group.evalPermissions(dctx, context)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets a list of required IN parameters in sequence.
     * @return A list of required IN parameters in the order which they were defined.
     */
    public List<Object> getInParameterSequence(Map<String, ?> source) {
        List<Object> target = new ArrayList<>(this.contextParamList.size()); // SCIPIO: switched to ArrayList
        if (source == null) {
            return target;
        }
        if (UtilValidate.isEmpty(contextInfo)) {
            return target;
        }
        for (ModelParam modelParam: this.contextParamList) {
            // don't include OUT parameters in this list, only IN and INOUT
            if (OUT_PARAM.equals(modelParam.mode)) {
                continue;
            }

            Object srcObject = source.get(modelParam.name);
            if (srcObject != null) {
                target.add(srcObject);
            }
        }
        return target;
    }

    /**
     * Returns a list of ModelParam objects in the order they were defined when
     * the service was created.
     */
    public List<ModelParam> getModelParamList() {
        return new ArrayList<>(this.contextParamList);
    }

    public List<ModelParam> getNamedModelParamList(String mode, Collection<String> paramNames) {
        List<ModelParam> targetParams = new ArrayList<>(paramNames.size());
        for (String paramName : paramNames) {
            ModelParam param = contextInfo.get(paramName);
            if (param != null) {
                targetParams.add(param);
            }
        }
        return targetParams;
    }

    public List<ModelParam> getNamedModelParamList(String mode, Object... paramNames) {
        return getNamedModelParamList(mode, Arrays.asList(paramNames));
    }

    public List<ModelParam> getParamModeModelParamList(String mode) {
        List<String> paramNames = PARAM_MODE_PARAMS_MAP.get(mode);
        return (paramNames != null) ? getNamedModelParamList(mode, paramNames) : null;
    }

    /**
     * Returns a list of ModelParam objects in the order they were defined when
     * the service was created.
     */
    public List<ModelParam> getInModelParamList() {
        List<ModelParam> inList = new ArrayList<>(this.contextParamList.size()); // SCIPIO: switched to ArrayList
        for (ModelParam modelParam: this.contextParamList) {
            // don't include OUT parameters in this list, only IN and INOUT
            if (OUT_PARAM.equals(modelParam.mode)) {
                continue;
            }
            inList.add(modelParam);
        }
        return inList;
    }

    /**
     * Returns a list of ModelParam objects in the order they were defined when
     * the service was created (SCIPIO).
     */
    public List<ModelParam> getOutModelParamList() {
        List<ModelParam> outList = new ArrayList<>(this.contextParamList.size());
        for (ModelParam modelParam: this.contextParamList) {
            // don't include IN parameters in this list, only OUT and INOUT
            if (IN_PARAM.equals(modelParam.mode)) {
                continue;
            }
            outList.add(modelParam);
        }
        return outList;
    }

    /**
     * Run the interface update and inherit all interface parameters.
     *
     * <p>SCIPIO: 3.0.0: Refactored to avoid use of {@link DispatchContext#getModelService(String)} to fix contention/threading there.</p>
     *
     * @param serviceMap The DispatchContext's service map to use for service lookups
     */
    public synchronized void interfaceUpdate(Map<String, ModelService> serviceMap) throws GenericServiceException {
        if (!inheritedParameters) {
            // SCIPIO: prevent stack overflows
            Integer maxCalls = IFC_CALLS.get();
            if (maxCalls != null && maxCalls > IFC_MAXCALLS) {
                throw new GenericServiceException("Interface update stack overflow for service [" + name + "]; " +
                        IFC_MAXCALLS + " calls reached");
            }
            try {
                IFC_CALLS.set(maxCalls != null ? maxCalls + 1 : 1);

                // SCIPIO: Also update overriddenService
                if (overriddenService != null && !overriddenService.inheritedParameters()) {
                    overriddenService.interfaceUpdate(serviceMap);
                }

                // services w/ engine 'group' auto-implement the grouped services
                if ("group".equals(this.engineName) && implServices.size() == 0) {
                    GroupModel group = internalGroup;
                    if (group == null) {
                        group = ServiceGroupReader.getGroupModel(this.location);
                    }
                    if (group != null) {
                        for (GroupServiceModel sm : group.getServices()) {
                            implServices.add(new ModelServiceIface(sm.getName(), sm.isOptional()));
                            if (Debug.verboseOn())
                                Debug.logVerbose("Adding service [" + sm.getName() + "] as interface of: [" + this.name + "]", module);
                        }
                    }
                }

                // handle interfaces
                if (UtilValidate.isNotEmpty(implServices) && serviceMap != null) {
                    // SCIPIO: newImplServices strips the overriddenService from the definition (makeshift solution for now)
                    // so that other code that needs to process service defs don't get stuck in endless loop (compatilibyt)
                    Set<ModelServiceIface> newImplServices = new LinkedHashSet<>();
                    for (ModelServiceIface iface : implServices) {
                        String serviceName = iface.getService();
                        boolean optional = iface.isOptional();

                        // SCIPIO
                        //ModelService model = dctx.getModelService(serviceName);
                        ModelService model;
                        if (overriddenService != null && serviceName.equals(overriddenService.name)) {
                            model = overriddenService;
                        } else {
                            // SCIPIO: 3.0.0: Instead of this, use the service map directly and run interfaceUpdate ourselves
                            //model = dctx.getModelService(serviceName);
                            model = serviceMap.get(serviceName);
                            if (model != null && !model.inheritedParameters()) {
                                model.interfaceUpdate(serviceMap);
                            }
                            newImplServices.add(iface);
                        }
                        if (model != null) {
                            for (ModelParam newParam : model.contextParamList) {
                                ModelParam existingParam = this.contextInfo.get(newParam.name);
                                if (existingParam != null) {
                                    // if the existing param is not INOUT and the newParam.mode is different from existingParam.mode, make the existing param optional and INOUT
                                    // TODO: this is another case where having different optional/required settings for IN and OUT would be quite valuable...
                                    if (!IN_OUT_PARAM.equals(existingParam.mode) && !existingParam.mode.equals(newParam.mode)) {
                                        existingParam.mode = IN_OUT_PARAM;
                                        if (existingParam.optional || newParam.optional) {
                                            existingParam.optional = true;
                                        }
                                    }
                                } else {
                                    ModelParam newParamClone = new ModelParam(newParam);
                                    if (optional) {
                                        // default option is to make this optional, however the service can override and
                                        // force the clone to use the parents setting.
                                        newParamClone.optional = true;
                                    }
                                    this.addParam(newParamClone);
                                }
                            }

                            // SCIPIO: 2018-11-23: Inherit custom properties
                            if (!model.properties.isEmpty()) {
                                Map<String, Object> newProperties = new LinkedHashMap<>(model.properties);
                                newProperties.putAll(this.properties);
                                this.properties = Collections.unmodifiableMap(newProperties);
                                if (Debug.verboseOn() && newProperties.size() > 0) {
                                    Debug.logVerbose("Merged properties for service '" + this.name + "': " + newProperties, module);
                                }
                            }
                        } else {
                            Debug.logWarning("Inherited model [" + serviceName + "] not found for [" + this.name + "]", module);
                        }
                    }
                    if (newImplServices.size() != implServices.size()) {
                        implServices = newImplServices;
                    }
                }

                // handle any override parameters
                if (UtilValidate.isNotEmpty(overrideParameters)) {
                    for (ModelParam overrideParam : overrideParameters) {
                        ModelParam existingParam = contextInfo.get(overrideParam.name);

                        // keep the list clean, remove it then add it back
                        contextParamList.remove(existingParam);

                        if (existingParam != null) {
                            // now re-write the parameters
                            if (UtilValidate.isNotEmpty(overrideParam.type)) {
                                existingParam.type = overrideParam.type;
                            }
                            if (UtilValidate.isNotEmpty(overrideParam.mode)) {
                                existingParam.mode = overrideParam.mode;
                            }
                            if (UtilValidate.isNotEmpty(overrideParam.entityName)) {
                                existingParam.entityName = overrideParam.entityName;
                            }
                            if (UtilValidate.isNotEmpty(overrideParam.fieldName)) {
                                existingParam.fieldName = overrideParam.fieldName;
                            }
                            if (UtilValidate.isNotEmpty(overrideParam.formLabel)) {
                                existingParam.formLabel = overrideParam.formLabel;
                            }
                            if (overrideParam.getDefaultValue() != null) {
                                existingParam.copyDefaultValue(overrideParam);
                            }
                            if (overrideParam.overrideFormDisplay) {
                                existingParam.formDisplay = overrideParam.formDisplay;
                            }
                            if (overrideParam.overrideOptional) {
                                existingParam.optional = overrideParam.optional;
                            }
                            if (UtilValidate.isNotEmpty(overrideParam.allowHtml)) {
                                existingParam.allowHtml = overrideParam.allowHtml;
                            }
                            addParam(existingParam);
                        } else {
                            Debug.logWarning("Override param found but no parameter existing; ignoring: " + overrideParam.name, module);
                        }
                    }
                }

            } finally {
                // SCIPIO
                if (maxCalls != null) {
                    IFC_CALLS.set(maxCalls);
                } else {
                    IFC_CALLS.remove();
                }
            }

            // set the flag so we don't do this again
            this.inheritedParameters = true;
        }
    }

    /**
     * if the service is declare as deprecated, create a log warning with the reason
     * <p>
     * SCIPIO: By default this now only logs if verbose on. This should not be a warning in every place,
     * because having deprecated services is normal. During loading, we don't want to see these,
     * only if they're invoked.
     */
    public void informIfDeprecated(boolean warn) {
        if (this.deprecatedUseInstead != null && (warn || Debug.verboseOn())) { // SCIPIO: warn or verbose
            StringBuilder informMsg = new StringBuilder("DEPRECATED: the service ")
                    .append(name).append( " has been deprecated and replaced by ").append(deprecatedUseInstead);
            if (this.deprecatedSince != null) {
                informMsg.append(", since ").append(deprecatedSince);
            }
            if (deprecatedReason != null) {
                informMsg.append(" because '").append(deprecatedReason).append("'");
            }
            if (warn) { // SCIPIO
                Debug.logWarning(informMsg.toString(), module);
            } else {
                Debug.logVerbose(informMsg.toString(), module);
            }
        }
    }

    /**
     * if the service is declare as deprecated, create a log warning with the reason
     */
    public void informIfDeprecated() {
        informIfDeprecated(true); // SCIPIO: now delegating
    }

    public Document toWSDL(String locationURI) throws WSDLException {
        WSDLFactory factory = WSDLFactory.newInstance();
        Definition def = factory.newDefinition();
        def.setTargetNamespace(TNS);
        def.addNamespace("xsd", XSD);
        def.addNamespace("tns", TNS);
        def.addNamespace("soap", "http://schemas.xmlsoap.org/wsdl/soap/");
        this.getWSDL(def, locationURI);
        return factory.newWSDLWriter().getDocument(def);
    }

    public void getWSDL(Definition def, String locationURI) throws WSDLException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        Document document = null;
        try {
            builder = factory.newDocumentBuilder();
            document = builder.newDocument();
        } catch (Exception e) {
            throw new WSDLException("can not create WSDL", ModelService.class.getName()); // SCIPIO: changed variable used for module name
        }
        def.setTypes(this.getTypes(document, def));

        // set the IN parameters
        Input input = def.createInput();
        Set<String> inParam = this.getInParamNames();
        Message inMessage = def.createMessage();
        inMessage.setQName(new QName(TNS, this.name + "Request"));
        inMessage.setUndefined(false);
        Part parametersPart = def.createPart();
        parametersPart.setName("map-Map");
        parametersPart.setTypeName(new QName(TNS, "map-Map"));
        inMessage.addPart(parametersPart);
        Element documentation = document.createElement("wsdl:documentation");
        for (String paramName : inParam) {
            ModelParam param = this.getParam(paramName);
            if (!param.internal) {
                Part part = param.getWSDLPart(def);
                Element attribute = document.createElement("attribute");
                attribute.setAttribute("name", paramName);
                attribute.setAttribute("type", part.getTypeName().getLocalPart());
                attribute.setAttribute("namespace", part.getTypeName().getNamespaceURI());
                attribute.setAttribute("java-class", param.type);
                attribute.setAttribute("optional", Boolean.toString(param.optional));
                documentation.appendChild(attribute);
            }
        }
        Element usernameAttr = document.createElement("attribute");
        usernameAttr.setAttribute("name", "login.username");
        usernameAttr.setAttribute("type", "std-String");
        usernameAttr.setAttribute("namespace", TNS);
        usernameAttr.setAttribute("java-class", String.class.getName());
        usernameAttr.setAttribute("optional", Boolean.toString(!this.auth));
        documentation.appendChild(usernameAttr);

        Element passwordAttr = document.createElement("attribute");
        passwordAttr.setAttribute("name", "login.password");
        passwordAttr.setAttribute("type", "std-String");
        passwordAttr.setAttribute("namespace", TNS);
        passwordAttr.setAttribute("java-class", String.class.getName());
        passwordAttr.setAttribute("optional", Boolean.toString(!this.auth));
        documentation.appendChild(passwordAttr);

        parametersPart.setDocumentationElement(documentation);
        def.addMessage(inMessage);
        input.setMessage(inMessage);

        // set the OUT parameters
        Output output = def.createOutput();
        Set<String> outParam = this.getOutParamNames();
        Message outMessage = def.createMessage();
        outMessage.setQName(new QName(TNS, this.name + "Response"));
        outMessage.setUndefined(false);
        Part resultsPart = def.createPart();
        resultsPart.setName("map-Map");
        resultsPart.setTypeName(new QName(TNS, "map-Map"));
        outMessage.addPart(resultsPart);
        documentation = document.createElement("wsdl:documentation");
        for (String paramName : outParam) {
            ModelParam param = this.getParam(paramName);
            if (!param.internal) {
                Part part = param.getWSDLPart(def);
                Element attribute = document.createElement("attribute");
                attribute.setAttribute("name", paramName);
                attribute.setAttribute("type", part.getTypeName().getLocalPart());
                attribute.setAttribute("namespace", part.getTypeName().getNamespaceURI());
                attribute.setAttribute("java-class", param.type);
                attribute.setAttribute("optional", Boolean.toString(param.optional));
                documentation.appendChild(attribute);
            }
        }
        resultsPart.setDocumentationElement(documentation);
        def.addMessage(outMessage);
        output.setMessage(outMessage);

        // set port type
        Operation operation = def.createOperation();
        operation.setName(this.name);
        operation.setUndefined(false);
        operation.setOutput(output);
        operation.setInput(input);

        PortType portType = def.createPortType();
        portType.setQName(new QName(TNS, this.name + "PortType"));
        portType.addOperation(operation);
        portType.setUndefined(false);
        def.addPortType(portType);

        // SOAP binding
        SOAPBinding soapBinding = new SOAPBindingImpl();
        soapBinding.setStyle("rpc");
        soapBinding.setTransportURI("http://schemas.xmlsoap.org/soap/http");

        Binding binding = def.createBinding();
        binding.setQName(new QName(TNS, this.name + "SoapBinding"));
        binding.setPortType(portType);
        binding.setUndefined(false);
        binding.addExtensibilityElement(soapBinding);

        BindingOperation bindingOperation = def.createBindingOperation();
        bindingOperation.setName(operation.getName());
        bindingOperation.setOperation(operation);

        SOAPBody soapBody = new SOAPBodyImpl();
        soapBody.setUse("literal");
        soapBody.setNamespaceURI(TNS);
        soapBody.setEncodingStyles(UtilMisc.toList("http://schemas.xmlsoap.org/soap/encoding/"));

        BindingOutput bindingOutput = def.createBindingOutput();
        bindingOutput.addExtensibilityElement(soapBody);
        bindingOperation.setBindingOutput(bindingOutput);

        BindingInput bindingInput = def.createBindingInput();
        bindingInput.addExtensibilityElement(soapBody);
        bindingOperation.setBindingInput(bindingInput);

        SOAPOperation soapOperation = new SOAPOperationImpl();
        // soapAction should be set to the location of the SOAP URI, or Visual Studio won't construct the correct SOAP message
        soapOperation.setSoapActionURI(locationURI);
        // this is the RPC/literal style.  See http://www.ibm.com/developerworks/webservices/library/ws-whichwsdl/
        // this parameter is necessary or Apache Synapse won't recognize the WSDL
        soapOperation.setStyle("rpc");
        bindingOperation.addExtensibilityElement(soapOperation);

        binding.addBindingOperation(bindingOperation);
        def.addBinding(binding);

        // Service port
        Port port = def.createPort();
        port.setBinding(binding);
        port.setName(this.name + "Port");

        if (locationURI != null) {
            SOAPAddress soapAddress = new SOAPAddressImpl();
            soapAddress.setLocationURI(locationURI);
            port.addExtensibilityElement(soapAddress);
        }

        Service service = def.createService();
        service.setQName(new QName(TNS, this.name));
        service.addPort(port);
        def.addService(service);
    }

    public Types getTypes(Document document, Definition def) {
        Types types = def.createTypes();
        /* Schema */
        Element schema = document.createElement("xsd:schema");
        schema.setAttribute("targetNamespace", TNS);

        /*-----------------------------------*/
        /*--------- Standard Objects --------*/
        /*-----------------------------------*/

        /* null Element */
        Element stdNullElement = document.createElement("xsd:element");
        stdNullElement.setAttribute("name", "null");
        stdNullElement.setAttribute("nillable", "true");
        Element stdNullElement0 = document.createElement("xsd:complexType");
        stdNullElement.appendChild(stdNullElement0);
        Element stdNullElement1 = document.createElement("xsd:attribute");
        stdNullElement0.appendChild(stdNullElement1);
        stdNullElement1.setAttribute("name", "value");
        stdNullElement1.setAttribute("type", "xsd:string");
        stdNullElement1.setAttribute("use", "required");
        schema.appendChild(stdNullElement);
        /* std-String Element */
        Element stdStringElement = document.createElement("xsd:element");
        stdStringElement.setAttribute("name", "std-String");
        Element stdStringElement0 = document.createElement("xsd:complexType");
        stdStringElement.appendChild(stdStringElement0);
        Element stdStringElement1 = document.createElement("xsd:attribute");
        stdStringElement0.appendChild(stdStringElement1);
        stdStringElement1.setAttribute("name", "value");
        stdStringElement1.setAttribute("type", "xsd:string");
        stdStringElement1.setAttribute("use", "required");
        schema.appendChild(stdStringElement);
        /* std-Integer Element */
        Element stdIntegerElement = document.createElement("xsd:element");
        stdIntegerElement.setAttribute("name", "std-Integer");
        Element stdIntegerElement0 = document.createElement("xsd:complexType");
        stdIntegerElement.appendChild(stdIntegerElement0);
        Element stdIntegerElement1 = document.createElement("xsd:attribute");
        stdIntegerElement0.appendChild(stdIntegerElement1);
        stdIntegerElement1.setAttribute("name", "value");
        stdIntegerElement1.setAttribute("type", "xsd:integer");
        stdIntegerElement1.setAttribute("use", "required");
        schema.appendChild(stdIntegerElement);
        /* std-Long Element */
        Element stdLongElement = document.createElement("xsd:element");
        stdLongElement.setAttribute("name", "std-Long");
        Element stdLongElement0 = document.createElement("xsd:complexType");
        stdLongElement.appendChild(stdLongElement0);
        Element stdLongElement1 = document.createElement("xsd:attribute");
        stdLongElement0.appendChild(stdLongElement1);
        stdLongElement1.setAttribute("name", "value");
        stdLongElement1.setAttribute("type", "xsd:long");
        stdLongElement1.setAttribute("use", "required");
        schema.appendChild(stdLongElement);
        /* std-Float Element */
        Element stdFloatElement = document.createElement("xsd:element");
        stdFloatElement.setAttribute("name", "std-Float");
        Element stdFloatElement0 = document.createElement("xsd:complexType");
        stdFloatElement.appendChild(stdFloatElement0);
        Element stdFloatElement1 = document.createElement("xsd:attribute");
        stdFloatElement0.appendChild(stdFloatElement1);
        stdFloatElement1.setAttribute("name", "value");
        stdFloatElement1.setAttribute("type", "xsd:float");
        stdFloatElement1.setAttribute("use", "required");
        schema.appendChild(stdFloatElement);
        /* std-Double Element */
        Element stdDoubleElement = document.createElement("xsd:element");
        stdDoubleElement.setAttribute("name", "std-Double");
        Element stdDoubleElement0 = document.createElement("xsd:complexType");
        stdDoubleElement.appendChild(stdDoubleElement0);
        Element stdDoubleElement1 = document.createElement("xsd:attribute");
        stdDoubleElement0.appendChild(stdDoubleElement1);
        stdDoubleElement1.setAttribute("name", "value");
        stdDoubleElement1.setAttribute("type", "xsd:double");
        stdDoubleElement1.setAttribute("use", "required");
        schema.appendChild(stdDoubleElement);
        /* std-Boolean Element */
        Element stdBooleanElement = document.createElement("xsd:element");
        stdBooleanElement.setAttribute("name", "std-Boolean");
        Element stdBooleanElement0 = document.createElement("xsd:complexType");
        stdBooleanElement.appendChild(stdBooleanElement0);
        Element stdBooleanElement1 = document.createElement("xsd:attribute");
        stdBooleanElement0.appendChild(stdBooleanElement1);
        stdBooleanElement1.setAttribute("name", "value");
        stdBooleanElement1.setAttribute("type", "xsd:boolean");
        stdBooleanElement1.setAttribute("use", "required");
        schema.appendChild(stdBooleanElement);
        /* std-Locale Element */
        Element stdLocaleElement = document.createElement("xsd:element");
        stdLocaleElement.setAttribute("name", "std-Locale");
        Element stdLocaleElement0 = document.createElement("xsd:complexType");
        stdLocaleElement.appendChild(stdLocaleElement0);
        Element stdLocaleElement1 = document.createElement("xsd:attribute");
        stdLocaleElement0.appendChild(stdLocaleElement1);
        stdLocaleElement1.setAttribute("name", "value");
        stdLocaleElement1.setAttribute("type", "xsd:string");
        stdLocaleElement1.setAttribute("use", "required");
        schema.appendChild(stdLocaleElement);
        /* std-BigDecimal Element */
        Element stdBigDecimalElement = document.createElement("xsd:element");
        stdBigDecimalElement.setAttribute("name", "std-BigDecimal");
        Element stdBigDecimalElement0 = document.createElement("xsd:complexType");
        stdBigDecimalElement.appendChild(stdBigDecimalElement0);
        Element stdBigDecimalElement1 = document.createElement("xsd:attribute");
        stdBigDecimalElement0.appendChild(stdBigDecimalElement1);
        stdBigDecimalElement1.setAttribute("name", "value");
        stdBigDecimalElement1.setAttribute("type", "xsd:decimal");
        stdBigDecimalElement1.setAttribute("use", "required");
        schema.appendChild(stdBigDecimalElement);

        /*-----------------------------------*/
        /*----------- SQL Objects -----------*/
        /*-----------------------------------*/

        /* sql-Timestamp Element */
        Element sqlTimestampElement = document.createElement("xsd:element");
        sqlTimestampElement.setAttribute("name", "sql-Timestamp");
        Element sqlTimestampElement0 = document.createElement("xsd:complexType");
        sqlTimestampElement.appendChild(sqlTimestampElement0);
        Element sqlTimestampElement1 = document.createElement("xsd:attribute");
        sqlTimestampElement0.appendChild(sqlTimestampElement1);
        sqlTimestampElement1.setAttribute("name", "value");
        sqlTimestampElement1.setAttribute("type", "xsd:dateTime");
        sqlTimestampElement1.setAttribute("use", "required");
        schema.appendChild(sqlTimestampElement);
        /* sql-Date Element */
        Element sqlDateElement = document.createElement("xsd:element");
        sqlDateElement.setAttribute("name", "sql-Date");
        Element sqlDateElement0 = document.createElement("xsd:complexType");
        sqlDateElement.appendChild(sqlDateElement0);
        Element sqlDateElement1 = document.createElement("xsd:attribute");
        sqlDateElement0.appendChild(sqlDateElement1);
        sqlDateElement1.setAttribute("name", "value");
        sqlDateElement1.setAttribute("type", "xsd:date");
        sqlDateElement1.setAttribute("use", "required");
        schema.appendChild(sqlDateElement);
        /* sql-Time Element */
        Element sqlTimeElement = document.createElement("xsd:element");
        sqlTimeElement.setAttribute("name", "sql-Time");
        Element sqlTimeElement0 = document.createElement("xsd:complexType");
        sqlTimeElement.appendChild(sqlTimeElement0);
        Element sqlTimeElement1 = document.createElement("xsd:attribute");
        sqlTimeElement0.appendChild(sqlTimeElement1);
        sqlTimeElement1.setAttribute("name", "value");
        sqlTimeElement1.setAttribute("type", "xsd:time");
        sqlTimeElement1.setAttribute("use", "required");
        schema.appendChild(sqlTimeElement);

        /*-----------------------------------*/
        /*----------- List Objects -----------*/
        /*-----------------------------------*/

        /* col-ArrayList Element */
        Element colArrayListElement = document.createElement("xsd:element");
        colArrayListElement.setAttribute("name", "col-ArrayList");
        colArrayListElement.setAttribute("type", "tns:col-Collection");
        schema.appendChild(colArrayListElement);
        /* col-LinkedList Element */
        Element colLinkedListElement = document.createElement("xsd:element");
        colLinkedListElement.setAttribute("name", "col-LinkedList");
        colLinkedListElement.setAttribute("type", "tns:col-Collection");
        schema.appendChild(colLinkedListElement);
        /* col-Stack Element */
        Element colStackElement = document.createElement("xsd:element");
        colStackElement.setAttribute("name", "col-Stack");
        colStackElement.setAttribute("type", "tns:col-Collection");
        schema.appendChild(colStackElement);
        /* col-Vector Element */
        Element colVectorElement = document.createElement("xsd:element");
        colVectorElement.setAttribute("name", "col-Vector");
        colVectorElement.setAttribute("type", "tns:col-Collection");
        schema.appendChild(colVectorElement);
        /* col-TreeSet Element */
        Element colTreeSetElement = document.createElement("xsd:element");
        colTreeSetElement.setAttribute("name", "col-TreeSet");
        colTreeSetElement.setAttribute("type", "tns:col-Collection");
        schema.appendChild(colTreeSetElement);
        /* col-HashSet Element */
        Element colHashSetElement = document.createElement("xsd:element");
        colHashSetElement.setAttribute("name", "col-HashSet");
        colHashSetElement.setAttribute("type", "tns:col-Collection");
        schema.appendChild(colHashSetElement);
        /* col-Collection Element */
        Element colCollectionElement = document.createElement("xsd:element");
        colCollectionElement.setAttribute("name", "col-Collection");
        colCollectionElement.setAttribute("type", "tns:col-Collection");
        schema.appendChild(colCollectionElement);

        /*-----------------------------------*/
        /*----------- Map Objects -----------*/
        /*-----------------------------------*/

        /* map-TreeMap Element */
        Element mapTreeMapElement = document.createElement("xsd:element");
        mapTreeMapElement.setAttribute("name", "map-TreeMap");
        mapTreeMapElement.setAttribute("type", "tns:map-Map");
        schema.appendChild(mapTreeMapElement);
        /* map-WeakHashMap Element */
        Element mapWeakHashMapElement = document.createElement("xsd:element");
        mapWeakHashMapElement.setAttribute("name", "map-WeakHashMap");
        mapWeakHashMapElement.setAttribute("type", "tns:map-Map");
        schema.appendChild(mapWeakHashMapElement);
        /* map-Hashtable Element */
        Element mapHashtableElement = document.createElement("xsd:element");
        mapHashtableElement.setAttribute("name", "map-Hashtable");
        mapHashtableElement.setAttribute("type", "tns:map-Map");
        schema.appendChild(mapHashtableElement);
        /* map-Properties Element */
        Element mapPropertiesElement = document.createElement("xsd:element");
        mapPropertiesElement.setAttribute("name", "map-Properties");
        mapPropertiesElement.setAttribute("type", "tns:map-Map");
        schema.appendChild(mapPropertiesElement);
        /* map-HashMap Element */
        Element mapHashMapElement = document.createElement("xsd:element");
        mapHashMapElement.setAttribute("name", "map-HashMap");
        mapHashMapElement.setAttribute("type", "tns:map-Map");
        schema.appendChild(mapHashMapElement);
        /* map-Map Element */
        Element mapMapElement = document.createElement("xsd:element");
        mapMapElement.setAttribute("name", "map-Map");
        mapMapElement.setAttribute("type", "tns:map-Map");
        schema.appendChild(mapMapElement);
        /* map-Entry Element */
        Element mapEntryElement = document.createElement("xsd:element");
        mapEntryElement.setAttribute("name", "map-Entry");
        mapEntryElement.setAttribute("type", "tns:map-Entry");
        schema.appendChild(mapEntryElement);
        /* map-Key Element */
        Element mapKeyElement = document.createElement("xsd:element");
        mapKeyElement.setAttribute("name", "map-Key");
        mapKeyElement.setAttribute("type", "tns:map-Key");
        schema.appendChild(mapKeyElement);
        /* map-Value Element */
        Element mapValueElement = document.createElement("xsd:element");
        mapValueElement.setAttribute("name", "map-Value");
        mapValueElement.setAttribute("type", "tns:map-Value");
        schema.appendChild(mapValueElement);
        /* eepk- Element */
        Element eepkElement = document.createElement("xsd:element");
        eepkElement.setAttribute("name", "eepk-");
        eepkElement.setAttribute("type", "tns:map-Map");
        Element eepkElement0 = document.createElement("xsd:annotation");
        eepkElement.appendChild(eepkElement0);
        Element eepkElement1 = document.createElement("xsd:documentation");
        eepkElement0.appendChild(eepkElement1);
        eepkElement1.setTextContent("The name of element need to be appended with name of entity such as eepk-Product for Product entity.");
        schema.appendChild(eepkElement);
        /* eeval- Element */
        Element eevalElement = document.createElement("xsd:element");
        eevalElement.setAttribute("name", "eeval-");
        eevalElement.setAttribute("type", "tns:map-Map");
        Element eevalElement0 = document.createElement("xsd:annotation");
        eevalElement.appendChild(eevalElement0);
        Element eevalElement1 = document.createElement("xsd:documentation");
        eevalElement0.appendChild(eevalElement1);
        eevalElement1.setTextContent("The name of element need to be appended with name of entity such as eeval-Product for Product entity.");
        schema.appendChild(eevalElement);

        /*-----------------------------------*/
        /*----------- Custom Objects -----------*/
        /*-----------------------------------*/

        /* cus-obj Element */
        Element cusObjElement = document.createElement("xsd:element");
        cusObjElement.setAttribute("name", "cus-obj");
        Element cusObjElement0 = document.createElement("xsd:annotation");
        cusObjElement.appendChild(cusObjElement0);
        Element cusObjElement1 = document.createElement("xsd:documentation");
        cusObjElement0.appendChild(cusObjElement1);
        cusObjElement1.setTextContent("Object content is hex encoded so does not need to be in a CDATA block.");
        schema.appendChild(cusObjElement);

        /*-----------------------------------*/
        /*---------- Complex Types ----------*/
        /*-----------------------------------*/

        /* map-Map Complex Type */
        Element mapMapComplexType = document.createElement("xsd:complexType");
        mapMapComplexType.setAttribute("name", "map-Map");
        Element mapMapComplexType0 = document.createElement("xsd:sequence");
        mapMapComplexType.appendChild(mapMapComplexType0);
        Element mapMapComplexType1 = document.createElement("xsd:element");
        mapMapComplexType1.setAttribute("ref", "tns:map-Entry");
        mapMapComplexType1.setAttribute("minOccurs", "0");
        mapMapComplexType1.setAttribute("maxOccurs", "unbounded");
        mapMapComplexType0.appendChild(mapMapComplexType1);
        schema.appendChild(mapMapComplexType);
        /* map-Entry Complex Type */
        Element mapEntryComplexType = document.createElement("xsd:complexType");
        mapEntryComplexType.setAttribute("name", "map-Entry");
        Element mapEntryComplexType0 = document.createElement("xsd:sequence");
        mapEntryComplexType.appendChild(mapEntryComplexType0);
        Element mapEntryComplexType1 = document.createElement("xsd:element");
        mapEntryComplexType1.setAttribute("ref", "tns:map-Key");
        mapEntryComplexType1.setAttribute("minOccurs", "1");
        mapEntryComplexType1.setAttribute("maxOccurs", "1");
        mapEntryComplexType0.appendChild(mapEntryComplexType1);
        Element mapEntryComplexType2 = document.createElement("xsd:element");
        mapEntryComplexType2.setAttribute("ref", "tns:map-Value");
        mapEntryComplexType2.setAttribute("minOccurs", "1");
        mapEntryComplexType2.setAttribute("maxOccurs", "1");
        mapEntryComplexType0.appendChild(mapEntryComplexType2);
        schema.appendChild(mapEntryComplexType);
        /* map-Key Complex Type */
        Element mapKeyComplexType = document.createElement("xsd:complexType");
        mapKeyComplexType.setAttribute("name", "map-Key");
        Element mapKeyComplexType0 = document.createElement("xsd:all");
        mapKeyComplexType.appendChild(mapKeyComplexType0);
        Element mapKeyComplexType1 = document.createElement("xsd:element");
        mapKeyComplexType1.setAttribute("ref", "tns:std-String");
        mapKeyComplexType1.setAttribute("minOccurs", "1");
        mapKeyComplexType1.setAttribute("maxOccurs", "1");
        mapKeyComplexType0.appendChild(mapKeyComplexType1);
        schema.appendChild(mapKeyComplexType);
        /* map-Value Complex Type */
        Element mapValueComplexType = document.createElement("xsd:complexType");
        mapValueComplexType.setAttribute("name", "map-Value");
        Element mapValueComplexType0 = document.createElement("xsd:choice");
        mapValueComplexType.appendChild(mapValueComplexType0);
        Element mapValueComplexTypeNull = document.createElement("xsd:element");
        mapValueComplexTypeNull.setAttribute("ref", "tns:null");
        mapValueComplexTypeNull.setAttribute("minOccurs", "1");
        mapValueComplexTypeNull.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexTypeNull);
        Element mapValueComplexType1 = document.createElement("xsd:element");
        mapValueComplexType1.setAttribute("ref", "tns:std-String");
        mapValueComplexType1.setAttribute("minOccurs", "1");
        mapValueComplexType1.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType1);
        Element mapValueComplexType2 = document.createElement("xsd:element");
        mapValueComplexType2.setAttribute("ref", "tns:std-Integer");
        mapValueComplexType2.setAttribute("minOccurs", "1");
        mapValueComplexType2.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType2);
        Element mapValueComplexType3 = document.createElement("xsd:element");
        mapValueComplexType3.setAttribute("ref", "tns:std-Long");
        mapValueComplexType3.setAttribute("minOccurs", "1");
        mapValueComplexType3.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType3);
        Element mapValueComplexType4 = document.createElement("xsd:element");
        mapValueComplexType4.setAttribute("ref", "tns:std-Float");
        mapValueComplexType4.setAttribute("minOccurs", "1");
        mapValueComplexType4.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType4);
        Element mapValueComplexType5 = document.createElement("xsd:element");
        mapValueComplexType5.setAttribute("ref", "tns:std-Double");
        mapValueComplexType5.setAttribute("minOccurs", "1");
        mapValueComplexType5.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType5);
        Element mapValueComplexType6 = document.createElement("xsd:element");
        mapValueComplexType6.setAttribute("ref", "tns:std-Boolean");
        mapValueComplexType6.setAttribute("minOccurs", "1");
        mapValueComplexType6.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType6);
        Element mapValueComplexType7 = document.createElement("xsd:element");
        mapValueComplexType7.setAttribute("ref", "tns:std-Locale");
        mapValueComplexType7.setAttribute("minOccurs", "1");
        mapValueComplexType7.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType7);
        Element mapValueComplexType8 = document.createElement("xsd:element");
        mapValueComplexType8.setAttribute("ref", "tns:sql-Timestamp");
        mapValueComplexType8.setAttribute("minOccurs", "1");
        mapValueComplexType8.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType8);
        Element mapValueComplexType9 = document.createElement("xsd:element");
        mapValueComplexType9.setAttribute("ref", "tns:sql-Date");
        mapValueComplexType9.setAttribute("minOccurs", "1");
        mapValueComplexType9.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType9);
        Element mapValueComplexType10 = document.createElement("xsd:element");
        mapValueComplexType10.setAttribute("ref", "tns:sql-Time");
        mapValueComplexType10.setAttribute("minOccurs", "1");
        mapValueComplexType10.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType10);
        Element mapValueComplexType11 = document.createElement("xsd:element");
        mapValueComplexType11.setAttribute("ref", "tns:col-ArrayList");
        mapValueComplexType11.setAttribute("minOccurs", "1");
        mapValueComplexType11.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType11);
        Element mapValueComplexType12 = document.createElement("xsd:element");
        mapValueComplexType12.setAttribute("ref", "tns:col-LinkedList");
        mapValueComplexType12.setAttribute("minOccurs", "1");
        mapValueComplexType12.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType12);
        Element mapValueComplexType13 = document.createElement("xsd:element");
        mapValueComplexType13.setAttribute("ref", "tns:col-Stack");
        mapValueComplexType13.setAttribute("minOccurs", "1");
        mapValueComplexType13.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType13);
        Element mapValueComplexType14 = document.createElement("xsd:element");
        mapValueComplexType14.setAttribute("ref", "tns:col-Vector");
        mapValueComplexType14.setAttribute("minOccurs", "1");
        mapValueComplexType14.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType14);
        Element mapValueComplexType15 = document.createElement("xsd:element");
        mapValueComplexType15.setAttribute("ref", "tns:col-TreeSet");
        mapValueComplexType15.setAttribute("minOccurs", "1");
        mapValueComplexType15.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType15);
        Element mapValueComplexType16 = document.createElement("xsd:element");
        mapValueComplexType16.setAttribute("ref", "tns:col-HashSet");
        mapValueComplexType16.setAttribute("minOccurs", "1");
        mapValueComplexType16.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType16);
        Element mapValueComplexType17 = document.createElement("xsd:element");
        mapValueComplexType17.setAttribute("ref", "tns:col-Collection");
        mapValueComplexType17.setAttribute("minOccurs", "1");
        mapValueComplexType17.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType17);
        Element mapValueComplexType18 = document.createElement("xsd:element");
        mapValueComplexType18.setAttribute("ref", "tns:map-HashMap");
        mapValueComplexType18.setAttribute("minOccurs", "1");
        mapValueComplexType18.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType18);
        Element mapValueComplexType19 = document.createElement("xsd:element");
        mapValueComplexType19.setAttribute("ref", "tns:map-Properties");
        mapValueComplexType19.setAttribute("minOccurs", "1");
        mapValueComplexType19.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType19);
        Element mapValueComplexType20 = document.createElement("xsd:element");
        mapValueComplexType20.setAttribute("ref", "tns:map-Hashtable");
        mapValueComplexType20.setAttribute("minOccurs", "1");
        mapValueComplexType20.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType20);
        Element mapValueComplexType21 = document.createElement("xsd:element");
        mapValueComplexType21.setAttribute("ref", "tns:map-WeakHashMap");
        mapValueComplexType21.setAttribute("minOccurs", "1");
        mapValueComplexType21.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType21);
        Element mapValueComplexType22 = document.createElement("xsd:element");
        mapValueComplexType22.setAttribute("ref", "tns:map-TreeMap");
        mapValueComplexType22.setAttribute("minOccurs", "1");
        mapValueComplexType22.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType22);
        Element mapValueComplexType23 = document.createElement("xsd:element");
        mapValueComplexType23.setAttribute("ref", "tns:map-Map");
        mapValueComplexType23.setAttribute("minOccurs", "1");
        mapValueComplexType23.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType23);
        Element mapValueComplexType24 = document.createElement("xsd:element");
        mapValueComplexType24.setAttribute("ref", "tns:eepk-");
        mapValueComplexType24.setAttribute("minOccurs", "1");
        mapValueComplexType24.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType24);
        Element mapValueComplexType25 = document.createElement("xsd:element");
        mapValueComplexType25.setAttribute("ref", "tns:eeval-");
        mapValueComplexType25.setAttribute("minOccurs", "1");
        mapValueComplexType25.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType25);
        schema.appendChild(mapValueComplexType);
        Element mapValueComplexType26 = document.createElement("xsd:element");
        mapValueComplexType26.setAttribute("ref", "tns:std-BigDecimal");
        mapValueComplexType26.setAttribute("minOccurs", "1");
        mapValueComplexType26.setAttribute("maxOccurs", "1");
        mapValueComplexType0.appendChild(mapValueComplexType26);
        schema.appendChild(mapValueComplexType);

        /* col-Collection Complex Type */
        Element colCollectionComplexType = document.createElement("xsd:complexType");
        colCollectionComplexType.setAttribute("name", "col-Collection");
        Element colCollectionComplexType0 = document.createElement("xsd:choice");
        colCollectionComplexType.appendChild(colCollectionComplexType0);
        Element colCollectionComplexTypeNull = document.createElement("xsd:element");
        colCollectionComplexTypeNull.setAttribute("ref", "tns:null");
        colCollectionComplexTypeNull.setAttribute("minOccurs", "0");
        colCollectionComplexTypeNull.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexTypeNull);
        Element colCollectionComplexType1 = document.createElement("xsd:element");
        colCollectionComplexType1.setAttribute("ref", "tns:std-String");
        colCollectionComplexType1.setAttribute("minOccurs", "0");
        colCollectionComplexType1.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType1);
        Element colCollectionComplexType2 = document.createElement("xsd:element");
        colCollectionComplexType2.setAttribute("ref", "tns:std-Integer");
        colCollectionComplexType2.setAttribute("minOccurs", "0");
        colCollectionComplexType2.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType2);
        Element colCollectionComplexType3 = document.createElement("xsd:element");
        colCollectionComplexType3.setAttribute("ref", "tns:std-Long");
        colCollectionComplexType3.setAttribute("minOccurs", "0");
        colCollectionComplexType3.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType3);
        Element colCollectionComplexType4 = document.createElement("xsd:element");
        colCollectionComplexType4.setAttribute("ref", "tns:std-Float");
        colCollectionComplexType4.setAttribute("minOccurs", "0");
        colCollectionComplexType4.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType4);
        Element colCollectionComplexType5 = document.createElement("xsd:element");
        colCollectionComplexType5.setAttribute("ref", "tns:std-Double");
        colCollectionComplexType5.setAttribute("minOccurs", "0");
        colCollectionComplexType5.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType5);
        Element colCollectionComplexType6 = document.createElement("xsd:element");
        colCollectionComplexType6.setAttribute("ref", "tns:std-Boolean");
        colCollectionComplexType6.setAttribute("minOccurs", "0");
        colCollectionComplexType6.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType6);
        Element colCollectionComplexType7 = document.createElement("xsd:element");
        colCollectionComplexType7.setAttribute("ref", "tns:std-Locale");
        colCollectionComplexType7.setAttribute("minOccurs", "0");
        colCollectionComplexType7.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType7);
        Element colCollectionComplexType8 = document.createElement("xsd:element");
        colCollectionComplexType8.setAttribute("ref", "tns:sql-Timestamp");
        colCollectionComplexType8.setAttribute("minOccurs", "0");
        colCollectionComplexType8.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType8);
        Element colCollectionComplexType9 = document.createElement("xsd:element");
        colCollectionComplexType9.setAttribute("ref", "tns:sql-Date");
        colCollectionComplexType9.setAttribute("minOccurs", "0");
        colCollectionComplexType9.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType9);
        Element colCollectionComplexType10 = document.createElement("xsd:element");
        colCollectionComplexType10.setAttribute("ref", "tns:sql-Time");
        colCollectionComplexType10.setAttribute("minOccurs", "0");
        colCollectionComplexType10.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType10);
        Element colCollectionComplexType11 = document.createElement("xsd:element");
        colCollectionComplexType11.setAttribute("ref", "tns:col-ArrayList");
        colCollectionComplexType11.setAttribute("minOccurs", "0");
        colCollectionComplexType11.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType11);
        Element colCollectionComplexType12 = document.createElement("xsd:element");
        colCollectionComplexType12.setAttribute("ref", "tns:col-LinkedList");
        colCollectionComplexType12.setAttribute("minOccurs", "0");
        colCollectionComplexType12.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType12);
        Element colCollectionComplexType13 = document.createElement("xsd:element");
        colCollectionComplexType13.setAttribute("ref", "tns:col-Stack");
        colCollectionComplexType13.setAttribute("minOccurs", "0");
        colCollectionComplexType13.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType13);
        Element colCollectionComplexType14 = document.createElement("xsd:element");
        colCollectionComplexType14.setAttribute("ref", "tns:col-Vector");
        colCollectionComplexType14.setAttribute("minOccurs", "0");
        colCollectionComplexType14.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType14);
        Element colCollectionComplexType15 = document.createElement("xsd:element");
        colCollectionComplexType15.setAttribute("ref", "tns:col-TreeSet");
        colCollectionComplexType15.setAttribute("minOccurs", "0");
        colCollectionComplexType15.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType15);
        Element colCollectionComplexType16 = document.createElement("xsd:element");
        colCollectionComplexType16.setAttribute("ref", "tns:col-HashSet");
        colCollectionComplexType16.setAttribute("minOccurs", "0");
        colCollectionComplexType16.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType16);
        Element colCollectionComplexType17 = document.createElement("xsd:element");
        colCollectionComplexType17.setAttribute("ref", "tns:col-Collection");
        colCollectionComplexType17.setAttribute("minOccurs", "0");
        colCollectionComplexType17.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType17);
        Element colCollectionComplexType18 = document.createElement("xsd:element");
        colCollectionComplexType18.setAttribute("ref", "tns:map-HashMap");
        colCollectionComplexType18.setAttribute("minOccurs", "0");
        colCollectionComplexType18.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType18);
        Element colCollectionComplexType19 = document.createElement("xsd:element");
        colCollectionComplexType19.setAttribute("ref", "tns:map-Properties");
        colCollectionComplexType19.setAttribute("minOccurs", "0");
        colCollectionComplexType19.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType19);
        Element colCollectionComplexType20 = document.createElement("xsd:element");
        colCollectionComplexType20.setAttribute("ref", "tns:map-Hashtable");
        colCollectionComplexType20.setAttribute("minOccurs", "0");
        colCollectionComplexType20.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType20);
        Element colCollectionComplexType21 = document.createElement("xsd:element");
        colCollectionComplexType21.setAttribute("ref", "tns:map-WeakHashMap");
        colCollectionComplexType21.setAttribute("minOccurs", "0");
        colCollectionComplexType21.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType21);
        Element colCollectionComplexType22 = document.createElement("xsd:element");
        colCollectionComplexType22.setAttribute("ref", "tns:map-TreeMap");
        colCollectionComplexType22.setAttribute("minOccurs", "0");
        colCollectionComplexType22.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType22);
        Element colCollectionComplexType23 = document.createElement("xsd:element");
        colCollectionComplexType23.setAttribute("ref", "tns:map-Map");
        colCollectionComplexType23.setAttribute("minOccurs", "0");
        colCollectionComplexType23.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType23);
        Element colCollectionComplexType24 = document.createElement("xsd:element");
        colCollectionComplexType24.setAttribute("ref", "tns:eepk-");
        colCollectionComplexType24.setAttribute("minOccurs", "0");
        colCollectionComplexType24.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType24);
        Element colCollectionComplexType25 = document.createElement("xsd:element");
        colCollectionComplexType25.setAttribute("ref", "tns:eeval-");
        colCollectionComplexType25.setAttribute("minOccurs", "0");
        colCollectionComplexType25.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType25);
        schema.appendChild(colCollectionComplexType);
        Element colCollectionComplexType26 = document.createElement("xsd:element");
        colCollectionComplexType26.setAttribute("ref", "tns:std-BigDecimal");
        colCollectionComplexType26.setAttribute("minOccurs", "0");
        colCollectionComplexType26.setAttribute("maxOccurs", "unbounded");
        colCollectionComplexType0.appendChild(colCollectionComplexType26);
        schema.appendChild(colCollectionComplexType);

        types.setDocumentationElement(schema);
        return types;
    }

    /**
     * SCIPIO: 2018-09-10: additional load-time validation support.
     * <p>
     * For now this stops on the first issue, good enough for now.
     */
    protected boolean validateModel() {
        boolean valid = true;
        if ("entity-auto".equals(this.engineName) && UtilValidate.isEmpty(this.defaultEntityName)) {
            valid = false;
            Debug.logError("entity-auto service '" + name + "' does not specify a default-entity-name", module);
        }
        if (UtilValidate.isNotEmpty(location)) {
            if (location.startsWith("component://")) {
                URL resolvedLocation = null;
                try {
                    resolvedLocation = FlexibleLocation.resolveLocation(location);
                    if (resolvedLocation == null) {
                        throw new NullPointerException();
                    }
                    if (!new java.io.File(resolvedLocation.toURI()).exists()) {
                        throw new FileNotFoundException();
                    }
                } catch (Exception e) {
                    valid = false;
                    Debug.logError("Service '" + name + "' points to invalid file location: " + location
                            + " (" + e.toString() + ")", module);
                    resolvedLocation = null;
                }
                if (resolvedLocation != null) {
                    if ("simple".equals(engineName)) {
                        try {
                            Document document = null;
                            try {
                                document = UtilXml.readXmlDocument(resolvedLocation, true, true);
                            } catch (Exception e) {
                                throw new IllegalArgumentException("Could not read SimpleMethod XML document [" + resolvedLocation + "]: ", e);
                            }
                            boolean found = false;
                            for (Element simpleMethodElement : UtilXml.childElementList(document.getDocumentElement(), "simple-method")) {
                                String simpleMethodName = simpleMethodElement.getAttribute("method-name");
                                if (invoke.equals(simpleMethodName)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                throw new IllegalArgumentException("simple-method with name '" + invoke + "' not found");
                            }
                        } catch(Exception e) {
                            valid = false;
                            Debug.logError("simple-method service '" + name + "' points to an invalid method ("
                                    + invoke + ") in " + location + " (" + e.toString() + ")", module);
                        }
                    } else if ("groovy".equals(engineName)) {
                        // TODO: how?
                    }
                }
            } else if ("java".equals(engineName)) {
                // FIXME?: always ClassNotFoundException for thirdparty classes during loading, skip for now...
                if (!location.startsWith("org.ofbiz.accounting.thirdparty.") &&
                    !location.startsWith("org.ofbiz.content.openoffice.")) {
                    try {
                        ClassLoader cl = Thread.currentThread().getContextClassLoader();
                        Class<?> c = cl.loadClass(location);
                        try {
                            c.getMethod(invoke, DispatchContext.class, Map.class);
                        } catch(NoSuchMethodException e) {
                            // SCIPIO: 2.1.0: Additional overloads supported.
                            try {
                                c.getMethod(invoke, ServiceContext.class);
                            } catch(NoSuchMethodException e2) {
                                c.getMethod(invoke);
                            }
                        }
                    } catch(ClassNotFoundException e) {
                        valid = false;
                        Debug.logError("java service '" + name + "' points to an invalid class (" + location + ") (" + e.toString() + ")", module);
                    } catch(NoSuchMethodException e) {
                        valid = false;
                        Debug.logError("java service '" + name + "' points to an invalid method ("
                                + invoke + ") in class (" + location + ") (" + e.toString() + ")", module);
                    } catch(Exception e) {
                        valid = false;
                        Debug.logError("java service '" + name + "' points to an invalid method ("
                                + invoke + ") or class (" + location + ") (" + e.toString() + ")", module);
                    }
                }
            }
        }
        return valid;
    }

    /**
     * SCIPIO: Get the named custom property defined on this service.
     * Added 2018-11-23.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * SCIPIO: Checks if this service has any custom properties.
     * Added 2018-11-23.
     */
    public Object hasProperties() {
        return !properties.isEmpty();
    }

    /**
     * SCIPIO: Get the custom properties defined on this service.
     * Added 2018-11-23.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    /**
     * SCIPIO: Returns the definition location relative to project root.
     */
    public String getRelativeDefinitionLocation() {
        return relativeDefinitionLocation;
    }

    /**
     * SCIPIO: Returns true if the service should not log any non-vital information.
     */
    public boolean isQuiet() {
        return getLogLevel().isQuiet();
    }

    /**
     * SCIPIO: Returns configured logging level.
     */
    public LogLevel getLogLevel() {
        return logLevel;
    }

    /**
     * SCIPIO: Returns true if the service should not log any non-vital information related to ECA calls.
     */
    public boolean isEcaQuiet() {
        return getEcaLogLevel().isQuiet();
    }

    /**
     * SCIPIO: Returns configured ECA logging level.
     */
    public LogLevel getEcaLogLevel() {
        return ecaLogLevel;
    }

    /**
     * Filters out some trace logging based on dispatcher name.
     * <p>SCIPIO: 2.1.0: Added.</p>
     */
    public Pattern getLogTraceExcludeDispatcherRegex() {
        return logTraceExcludeDispatcherRegex;
    }

    /**
     * Filters out some trace logging based on dispatcher name.
     * <p>SCIPIO: 2.1.0: Added.</p>
     */
    public final boolean matchesLogTraceExcludeDispatcherRegex(String text) {
        return logTraceExcludeDispatcherRegex != null && logTraceExcludeDispatcherRegex.matcher(text).matches();
    }

    /**
     * SCIPIO: Returns the service attributes marked for auto-conversion.
     */
    public List<ModelParam> getTypeConvertParamList() {
        List<ModelParam> paramList = this.typeConvertParamList;
        if (paramList == null) {
            paramList = this.getModelParamList().stream().filter(p -> p.isTypeConvert())
                    .collect(Collectors.toCollection(ArrayList::new));
            ((ArrayList<ModelParam>) paramList).trimToSize();
            this.typeConvertParamList = Collections.unmodifiableList(paramList);
        }
        return paramList;
    }

    /**
     * SCIPIO: Returns the service this one overrode, or null.
     */
    public ModelService getOverriddenService() {
        return overriddenService;
    }

    /**
     * SCIPIO: Sets the service this one overrode, or null, or updates the parent overridden
     * service if already set - intended for loading only.
     * <p>This exists because the initial overriddenService is null when it comes from
     * another services xml file, but if there is both a service in a separate file and the
     * same, then you have to update the correct service back-pointer.</p>
     */
    protected void updateOverriddenService(ModelService overriddenService) {
        ModelService prevOverriddenService = this.overriddenService;
        if (prevOverriddenService != null) {
            prevOverriddenService.updateOverriddenService(overriddenService);
        } else {
            this.overriddenService = overriddenService;
            Debug.logInfo("Service [" + name +
                    "] redefinition: previous: [" + Objects.hashCode(overriddenService) + ", " +
                    overriddenService.contextParamList.size() + " params]; new: [" + Objects.hashCode(this) + ", " +
                    contextParamList.size() + " params]", module);
        }
    }

    /**
     * SCIPIO: Returns true if the given field name is in {@link #SYS_RESPONSE_FIELDS}.
     * @deprecated SCIPIO: 3.0.0: Use {@link #OUT_SYS_PARAMS}
     */
    @Deprecated
    public static boolean isSysResponseField(String fieldName) {
        return OUT_SYS_PARAMS.contains(fieldName);
    }

    /**
     * SCIPIO: Returns the default service priority for async and job services, or null if default.
     */
    public Long getPriority() {
        return priority;
    }

    /**
     * SCIPIO: Returns the default job pool when service invoked as persistent job, when not overridden by caller.
     */
    public String getJobPoolPersist() {
        return jobPoolPersist;
    }

    /**
     * Start delay, in milliseconds (thread sleep).
     * <p>SCIPIO: 2.1.0: Added start-delay service attribute.</p>
     */
    public Integer getStartDelay() {
        return startDelay;
    }

    /**
     * SCIPIO: Determines the effected priority for async and job service.
     * NOTE: This is NOT stored in JobSandbox.
     */
    public Long determinePriority(AsyncOptions options, Long defaultPriority) {
        if (options != null && options.priority != null) {
            return options.priority;
        } else if (getPriority() != null) {
            return getPriority();
        } else {
            return defaultPriority;
        }
    }

    /**
     * SCIPIO: Determines the effected priority for async and job service.
     * NOTE: This is NOT stored in JobSandbox.
     */
    public Long determinePriority(Long explicitPriority, Long defaultPriority) {
        return (explicitPriority != null) ? explicitPriority : (getPriority() != null ? getPriority() : defaultPriority);
    }

    public <T> T getJavaServiceReflectInfo() {
        return UtilGenerics.cast(javaServiceReflectInfo);
    }

    public void setJavaServiceReflectInfo(Object javaServiceReflectInfo) {
        this.javaServiceReflectInfo = javaServiceReflectInfo;
    }

    /**
     * For "IN-SYS", returns "IN".
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static String getParamModeIO(String mode) {
        int sep = mode.indexOf('-');
        return (sep >= 0) ? mode.substring(0, sep) : mode;
    }

    /**
     * For "IN-SYS", returns "SYS".
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static String getParamModeScope(String mode) {
        int sep = mode.indexOf('-');
        return (sep >= 0) ? mode.substring(sep + 1) : "";
    }

}
