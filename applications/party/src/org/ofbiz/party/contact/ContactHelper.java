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

package org.ofbiz.party.contact;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityUtil;

/**
 * Accessors for Contact Mechanisms
 */
public class ContactHelper {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public static final FlexibleStringExpander DEFAULT_TELECOM_EXDR =
            FlexibleStringExpander.getInstance("${countryCode}-${areaCode}-${contactNumber}${empty extension ? '' : ' ext. ' + extension}");
    public static final FlexibleStringExpander DEFAULT_FULLNAME_EXDR =
            FlexibleStringExpander.getInstance("${firstName} ${middleName} ${lastName}");

    private ContactHelper() {}

    public static Collection<GenericValue> getContactMech(GenericValue party, boolean includeOld) {
        return getContactMech(party, null, null, includeOld);
    }

    public static Collection<GenericValue> getContactMechByType(GenericValue party, String contactMechTypeId, boolean includeOld) {
        return getContactMech(party, null, contactMechTypeId, includeOld);
    }

    public static Collection<GenericValue> getContactMechByPurpose(GenericValue party, String contactMechPurposeTypeId, boolean includeOld) {
        return getContactMech(party, contactMechPurposeTypeId, null, includeOld);
    }

    public static Collection<GenericValue> getContactMech(GenericValue party, String contactMechPurposeTypeId, String contactMechTypeId, boolean includeOld) {
        if (party == null) {
            return null;
        }
        try {
            List<GenericValue> partyContactMechList;

            if (contactMechPurposeTypeId == null) {
                partyContactMechList = party.getRelated("PartyContactMech", null, null, false);
            } else {
                List<GenericValue> list;

                list = party.getRelated("PartyContactMechPurpose", UtilMisc.toMap("contactMechPurposeTypeId", contactMechPurposeTypeId), null, false);
                if (!includeOld) {
                    list = EntityUtil.filterByDate(list, true);
                }
                partyContactMechList = EntityUtil.getRelated("PartyContactMech", null, list, false);
            }
            if (!includeOld) {
                partyContactMechList = EntityUtil.filterByDate(partyContactMechList, true);
            }
            partyContactMechList = EntityUtil.orderBy(partyContactMechList, UtilMisc.toList("fromDate DESC"));
            if (contactMechTypeId == null) {
                return EntityUtil.getRelated("ContactMech", null, partyContactMechList, false);
            }
            return EntityUtil.getRelated("ContactMech", UtilMisc.toMap("contactMechTypeId", contactMechTypeId), partyContactMechList, false);
        } catch (GenericEntityException gee) {
            Debug.logWarning(gee, module);
            return Collections.emptyList();
        }
    }

    /**
     * Returns PartyContactMech instead of ContactMech.
     *
     * <p>NOTE: This does not validate any contactMechTypeId; typically these can be verified since POSTAL_ADDRESS and telecom are typically implicit.</p>
     *
     * <p>SCIPIO: 3.0.0: Added as analog of {@link #getContactMech(GenericValue, String, String, boolean)}.</p>
     */
    public static Collection<GenericValue> getPartyContactMech(GenericValue party, String contactMechPurposeTypeId, boolean includeOld) {
        if (party == null) {
            return null;
        }
        try {
            List<GenericValue> partyContactMechList;

            if (contactMechPurposeTypeId == null) {
                throw new NullPointerException("Missing contactMechPurposeTypeId");
                //partyContactMechList = party.getRelated("PartyContactMech", null, null, false);
            } else {
                List<GenericValue> list;

                list = party.getRelated("PartyContactMechPurpose", UtilMisc.toMap("contactMechPurposeTypeId", contactMechPurposeTypeId), null, false);
                if (!includeOld) {
                    list = EntityUtil.filterByDate(list, true);
                }
                partyContactMechList = EntityUtil.getRelated("PartyContactMech", null, list, false);
            }
            if (!includeOld) {
                partyContactMechList = EntityUtil.filterByDate(partyContactMechList, true);
            }
            partyContactMechList = EntityUtil.orderBy(partyContactMechList, UtilMisc.toList("fromDate DESC"));
            return partyContactMechList;
        } catch (GenericEntityException gee) {
            Debug.logWarning(gee, module);
            return Collections.emptyList();
        }
    }

    public static String formatCreditCard(GenericValue creditCardInfo) {
        StringBuilder result = new StringBuilder(16);

        result.append(creditCardInfo.getString("cardType"));
        String cardNumber = creditCardInfo.getString("cardNumber");

        if (cardNumber != null && cardNumber.length() > 4) {
            result.append(' ').append(cardNumber.substring(cardNumber.length() - 4));
        }
        result.append(' ').append(creditCardInfo.getString("expireDate"));
        return result.toString();
    }

    /**
     * Formats a telecom number according to expression, with some automatic cleanup.
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static String formatTelecomNumber(GenericValue partyContactMech, GenericValue contactMech, GenericValue telecomNumber,
                                             FlexibleStringExpander format, boolean cleanupFormat) {
        Map<String, Object> ctx = new HashMap<>(telecomNumber);
        ctx.put("extension", partyContactMech.getString("extension"));
        String number = format.expandString(ctx);
        if (cleanupFormat) {
            number = number.replaceAll("^[-\\s]+", "");
            number = number.replaceAll("[-]{2,}", "-");
            number = number.replaceAll("\\s{2,}", " ");
        }
        return number;
    }

    public static FlexibleStringExpander getDefaultTelecomExdr(Delegator delegator) {
        return DEFAULT_TELECOM_EXDR;
    }


    /**
     * Formats a person full name, with some automatic cleanup.
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static String formatFullName(GenericValue person, FlexibleStringExpander format, boolean cleanupFormat) {
        Map<String, Object> ctx = new HashMap<>(person);
        String number = format.expandString(ctx);
        if (cleanupFormat) {
            number = number.replaceAll("\\s{2,}", " ");
        }
        return number;
    }

    public static FlexibleStringExpander getDefaultFullNameExdr(Delegator delegator) {
        return DEFAULT_FULLNAME_EXDR;
    }

}
