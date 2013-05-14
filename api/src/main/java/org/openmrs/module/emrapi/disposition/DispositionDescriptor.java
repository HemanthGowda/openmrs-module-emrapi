/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.emrapi.disposition;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.emrapi.concept.EmrConceptService;
import org.openmrs.module.emrapi.descriptor.ConceptSetDescriptor;

/**
 * Describes the concepts necessary for recording a Disposition concept set
 */
public class DispositionDescriptor extends ConceptSetDescriptor {

    private Concept dispositionSetConcept;
    private Concept dispositionConcept;

    public DispositionDescriptor(ConceptService conceptService) {
        setup(conceptService, EmrApiConstants.EMR_CONCEPT_SOURCE_NAME,
                "dispositionSetConcept", EmrApiConstants.CONCEPT_CODE_DISPOSITION_CONCEPT_SET,
                "dispositionConcept", EmrApiConstants.CONCEPT_CODE_DISPOSITION);
    }

    /**
     * Used for testing -- in production you'll use the constructor that takes ConceptService
     */
    public DispositionDescriptor() {
    }

    public Concept getDispositionSetConcept() {
        return dispositionSetConcept;
    }

    public void setDispositionSetConcept(Concept dispositionSetConcept) {
        this.dispositionSetConcept = dispositionSetConcept;
    }

    public Concept getDispositionConcept() {
        return dispositionConcept;
    }

    public void setDispositionConcept(Concept dispositionConcept) {
        this.dispositionConcept = dispositionConcept;
    }

    public Obs buildObsGroup(Disposition disposition, EmrConceptService emrConceptService) {
        Obs dispoObs = new Obs();
        dispoObs.setConcept(dispositionConcept);
        dispoObs.setValueCoded(emrConceptService.getConcept(disposition.getConceptCode()));

        Obs group = new Obs();
        group.setConcept(dispositionSetConcept);
        group.addGroupMember(dispoObs);
        return group;
    }

}