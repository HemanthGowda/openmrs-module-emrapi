package org.openmrs.module.emrapi.visit;


import org.apache.commons.lang.time.DateUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.EmrApiProperties;
import org.openmrs.module.emrapi.disposition.DispositionDescriptor;
import org.openmrs.module.emrapi.disposition.DispositionService;
import org.openmrs.module.emrapi.test.MockMetadataTestUtil;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.HOUR;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static uk.co.it.modular.hamcrest.date.DateMatchers.within;

@PrepareForTest(Calendar.class)
@RunWith(PowerMockRunner.class)
public class VisitDomainWrapperTest {

    private VisitDomainWrapper visitDomainWrapper;
    private Visit visit;
    private EmrApiProperties emrApiProperties;
    private DispositionService dispositionService;


    @Before
    public void setUp(){
        visit = mock(Visit.class);
        emrApiProperties = mock(EmrApiProperties.class);
        dispositionService = mock(DispositionService.class);
        visitDomainWrapper = new VisitDomainWrapper(visit, emrApiProperties, dispositionService);
    }

    // this test was merged in when VisitSummary was merged into VisitDomainWrapper
    @Test
    public void shouldReturnMostRecentNonVoidedEncounterAndCheckInEncounter() throws Exception {
        EncounterType checkInEncounterType = new EncounterType();

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getCheckInEncounterType()).thenReturn(checkInEncounterType);

        Encounter checkIn = new Encounter();
        checkIn.setEncounterDatetime(DateUtils.addHours(new Date(), -3));
        checkIn.setEncounterType(checkInEncounterType);
        Encounter vitals = new Encounter();
        vitals.setEncounterDatetime(DateUtils.addHours(new Date(), -2));
        Encounter consult = new Encounter();
        consult.setEncounterDatetime(DateUtils.addHours(new Date(), -1));
        Encounter voided = new Encounter();
        voided.setVoided(true);
        consult.setEncounterDatetime(new Date());

        // per the hbm.xml file, visit.encounters are sorted by encounterDatetime desc
        Visit visit = new Visit();
        visit.setStartDatetime(checkIn.getEncounterDatetime());
        visit.setEncounters(new LinkedHashSet<Encounter>(4));
        visit.addEncounter(voided);
        visit.addEncounter(consult);
        visit.addEncounter(vitals);
        visit.addEncounter(checkIn);

        VisitDomainWrapper wrapper = new VisitDomainWrapper(visit, props);
        assertThat(wrapper.getCheckInEncounter(), is(checkIn));
        assertThat(wrapper.getMostRecentEncounter(), is(consult));
    }

    @Test
    public void shouldReturnDifferenceInDaysBetweenCurrentDateAndStartDate(){
        Calendar startDate = Calendar.getInstance();
        startDate.add(DAY_OF_MONTH, -5);

        when(visit.getStartDatetime()).thenReturn(startDate.getTime());

        int days = visitDomainWrapper.getDifferenceInDaysBetweenCurrentDateAndStartDate();

        assertThat(days, is(5));
    }

    @Test
    public void shouldReturnDifferenceInDaysBetweenCurrentDateAndStartDateWhenTimeIsDifferent(){

        Calendar today = Calendar.getInstance();
        today.set(HOUR, 7);

        Calendar startDate = Calendar.getInstance();
        startDate.add(DAY_OF_MONTH, -5);
        startDate.set(HOUR, 9);

        PowerMockito.mockStatic(Calendar.class);
        when(Calendar.getInstance()).thenReturn(today);

        when(visit.getStartDatetime()).thenReturn(startDate.getTime());

        int days = visitDomainWrapper.getDifferenceInDaysBetweenCurrentDateAndStartDate();

        assertThat(days, is(5));

    }

    @Test
    public void shouldNotBeAdmittedWhenNeverAdmitted() throws Exception {
        when(visit.getEncounters()).thenReturn(Collections.<Encounter>emptySet());
        visitDomainWrapper.setEmrApiProperties(mock(EmrApiProperties.class));

        assertFalse(visitDomainWrapper.isAdmitted());
    }

    @Test
    public void shouldBeAdmittedWhenAlreadyAdmitted() throws Exception {
        EncounterType admitEncounterType = new EncounterType();

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

        assertTrue(visitDomainWrapper.isAdmitted());
    }

    @Test
    public void shouldNotBeAdmittedWhenAdmittedAndDischarged() throws Exception {
        EncounterType admitEncounterType = new EncounterType();
        EncounterType dischargeEncounterType = new EncounterType();

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        when(props.getExitFromInpatientEncounterType()).thenReturn(dischargeEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -2));

        Encounter discharge = new Encounter();
        discharge.setEncounterType(dischargeEncounterType);
        discharge.setEncounterDatetime(DateUtils.addHours(new Date(), -1));

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(discharge);
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

        assertFalse(visitDomainWrapper.isAdmitted());
    }

    @Test
    public void shouldNotBeAdmittedWhenAdmissionIsVoided() throws Exception {
        EncounterType admitEncounterType = new EncounterType();

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -2));
        admit.setVoided(true);

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

        assertFalse(visitDomainWrapper.isAdmitted());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfDateOutsideOfVisit() {
        Date now = new Date();
        when(visit.getStartDatetime()).thenReturn(DateUtils.addHours(new Date(), -3));
        visitDomainWrapper.isAdmitted(DateUtils.addHours(now, -4));
    }

    @Test
    public void shouldNotBeAdmittedIfTestDateBeforeAdmitDate() throws Exception {

        EncounterType admitEncounterType = new EncounterType();
        EncounterType dischargeEncounterType = new EncounterType();

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        when(props.getExitFromInpatientEncounterType()).thenReturn(dischargeEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        when(visit.getStartDatetime()).thenReturn(DateUtils.addHours(new Date(), -5));

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -3));

        Encounter discharge = new Encounter();
        discharge.setEncounterType(dischargeEncounterType);
        discharge.setEncounterDatetime(DateUtils.addHours(new Date(), -1));

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(discharge);
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

       assertFalse(visitDomainWrapper.isAdmitted(DateUtils.addHours(new Date(), -4)));
    }

    @Test
    public void shouldBeAdmittedIfTestDateBetweenAdmissionAndDischargeDate() throws Exception {

        EncounterType admitEncounterType = new EncounterType();
        EncounterType dischargeEncounterType = new EncounterType();

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        when(props.getExitFromInpatientEncounterType()).thenReturn(dischargeEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        when(visit.getStartDatetime()).thenReturn(DateUtils.addHours(new Date(), -5));

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -3));

        Encounter discharge = new Encounter();
        discharge.setEncounterType(dischargeEncounterType);
        discharge.setEncounterDatetime(DateUtils.addHours(new Date(), -1));

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(discharge);
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

        assertTrue(visitDomainWrapper.isAdmitted(DateUtils.addHours(new Date(), -2)));
    }

    @Test
    public void shouldNotAdmittedIfTestDateAfterAndDischargeDate() throws Exception {

        EncounterType admitEncounterType = new EncounterType();
        EncounterType dischargeEncounterType = new EncounterType();

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        when(props.getExitFromInpatientEncounterType()).thenReturn(dischargeEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        when(visit.getStartDatetime()).thenReturn(DateUtils.addHours(new Date(), -5));

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -3));

        Encounter discharge = new Encounter();
        discharge.setEncounterType(dischargeEncounterType);
        discharge.setEncounterDatetime(DateUtils.addHours(new Date(), -1));

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(discharge);
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

        assertFalse(visitDomainWrapper.isAdmitted(new Date()));
    }

   @Test
   public void shouldReturnCurrentLocationForAdmittedPatient() {

       EncounterType admitEncounterType = new EncounterType();
       EncounterType transferEncounterType = new EncounterType();

       Location icu = new Location();
       Location surgery = new Location();

       when(visit.getStartDatetime()).thenReturn(DateUtils.addHours(new Date(), -5));

       EmrApiProperties props = mock(EmrApiProperties.class);
       when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
       when(props.getTransferWithinHospitalEncounterType()).thenReturn(transferEncounterType);
       visitDomainWrapper.setEmrApiProperties(props);

       Encounter admit = new Encounter();
       admit.setEncounterType(admitEncounterType);
       admit.setEncounterDatetime(DateUtils.addHours(new Date(), -3));
       admit.setLocation(icu);

       Encounter transfer = new Encounter();
       transfer.setEncounterType(transferEncounterType);
       transfer.setEncounterDatetime(DateUtils.addHours(new Date(), -1));
       transfer.setLocation(surgery);

       Set<Encounter> encounters = new LinkedHashSet<Encounter>();
       encounters.add(transfer);
       encounters.add(admit);
       when(visit.getEncounters()).thenReturn(encounters);

       assertThat(visitDomainWrapper.getInpatientLocation(DateUtils.addHours(new Date(), -2)), is(icu));
       assertThat(visitDomainWrapper.getInpatientLocation(new Date()), is(surgery));

   }

    @Test
    public void shouldReturnNullIfPatientNotAdmittedOnDate() {

        EncounterType admitEncounterType = new EncounterType();
        EncounterType transferEncounterType = new EncounterType();

        Location icu = new Location();

        when(visit.getStartDatetime()).thenReturn(DateUtils.addHours(new Date(), -5));

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        when(props.getTransferWithinHospitalEncounterType()).thenReturn(transferEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -3));
        admit.setLocation(icu);

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

        assertNull(visitDomainWrapper.getInpatientLocation(DateUtils.addHours(new Date(), -4)));
    }

    @Test
    public void shouldReturnNullIfPatientDischargedOnDate() {

        EncounterType admitEncounterType = new EncounterType();
        EncounterType dischargeEncounterType = new EncounterType();

        Location icu = new Location();
        when(visit.getStartDatetime()).thenReturn(DateUtils.addHours(new Date(), -5));

        EmrApiProperties props = mock(EmrApiProperties.class);
        when(props.getAdmissionEncounterType()).thenReturn(admitEncounterType);
        when(props.getExitFromInpatientEncounterType()).thenReturn(dischargeEncounterType);
        visitDomainWrapper.setEmrApiProperties(props);

        Encounter admit = new Encounter();
        admit.setEncounterType(admitEncounterType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -3));
        admit.setLocation(icu);

        Encounter discharge= new Encounter();
        discharge.setEncounterType(dischargeEncounterType);
        discharge.setEncounterDatetime(DateUtils.addHours(new Date(), -1));

        Set<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(discharge);
        encounters.add(admit);
        when(visit.getEncounters()).thenReturn(encounters);

        assertNull(visitDomainWrapper.getInpatientLocation(new Date()));
    }



    @Test
    public void shouldNotHaveEncounterWithoutSubsequentEncounterIfNoRelevantEncounters() throws Exception {
        when(visit.getEncounters()).thenReturn(new LinkedHashSet<Encounter>());
        assertFalse(visitDomainWrapper.hasEncounterWithoutSubsequentEncounter(new EncounterType(), new EncounterType()));
    }

    @Test
    public void shouldHaveEncounterWithoutSubsequentEncounterIfOneEncounterOfCorrectType() throws Exception {
        EncounterType targetType = new EncounterType();

        Encounter encounter = new Encounter();
        encounter.setEncounterType(targetType);
        encounter.setEncounterDatetime(new Date());

        LinkedHashSet<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(encounter);

        when(visit.getEncounters()).thenReturn(encounters);
        assertTrue(visitDomainWrapper.hasEncounterWithoutSubsequentEncounter(targetType, null));
    }

    @Test
    public void shouldNotHaveEncounterWithoutSubsequentEncounterIfOneEncounterOfWrongType() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setEncounterType(new EncounterType());
        encounter.setEncounterDatetime(new Date());

        LinkedHashSet<Encounter> encounters = new LinkedHashSet<Encounter>();
        encounters.add(encounter);

        when(visit.getEncounters()).thenReturn(encounters);
        assertFalse(visitDomainWrapper.hasEncounterWithoutSubsequentEncounter(new EncounterType(), null));
    }

    @Test
    public void shouldHaveEncounterWithoutSubsequentEncounterIfMostRecentRelevantEncounterIsOfCorrectType() throws Exception {
        EncounterType lookForType = new EncounterType();
        EncounterType cancelType = new EncounterType();

        Encounter admit = new Encounter();
        admit.setEncounterType(lookForType);
        admit.setEncounterDatetime(DateUtils.addHours(new Date(), -3));

        Encounter discharge = new Encounter();
        discharge.setEncounterType(cancelType);
        discharge.setEncounterDatetime(DateUtils.addHours(new Date(), -2));

        Encounter admitAgain = new Encounter();
        admitAgain.setEncounterType(lookForType);
        admitAgain.setEncounterDatetime(DateUtils.addHours(new Date(), -1));

        Encounter anotherEncounter = new Encounter();
        anotherEncounter.setEncounterType(new EncounterType());
        anotherEncounter.setEncounterDatetime(new Date());

        Set<Encounter> encounters = new HashSet<Encounter>();
        encounters.add(admit);
        encounters.add(discharge);
        encounters.add(admitAgain);
        encounters.add(anotherEncounter);

        when(visit.getEncounters()).thenReturn(encounters);
        assertTrue(visitDomainWrapper.hasEncounterWithoutSubsequentEncounter(lookForType, cancelType));
    }

    @Test
    public void shouldUseTheStopDateOfTheVisitForEncounterStopDateRange() {
        DateTime visitEndDate = new DateTime(2013, 1, 15, 12, 12, 12);

        Visit visit = new Visit();
        visit.setStopDatetime(visitEndDate.toDate());

        VisitDomainWrapper wrapper = new VisitDomainWrapper(visit);

        assertThat(wrapper.getEncounterStopDateRange(), is(visitEndDate.toDate()));
    }

    @Test
    public void shouldReturnNowForStopDateRangeIfStopDateOfTheVisitIsNull() {
        Visit visit = new Visit();
        visit.setStopDatetime(null);

        VisitDomainWrapper wrapper = new VisitDomainWrapper(visit);

        assertThat(wrapper.getEncounterStopDateRange(), within(1, SECONDS, new Date()));
    }

    @Test
    public void shouldReturnOldestNonVoidedEncounter() throws Exception {
        Visit visit = new Visit();

        Encounter voidedEncounter = new Encounter();
        voidedEncounter.setId(0);
        voidedEncounter.setVoided(true);

        Encounter encounter1 = new Encounter();
        encounter1.setId(1);
        encounter1.setEncounterDatetime(DateUtils.addMinutes(new Date(), -1));

        Encounter encounter2 = new Encounter();
        encounter2.setId(2);
        encounter2.setEncounterDatetime(new Date());

        visit.addEncounter(voidedEncounter);
        visit.addEncounter(encounter2);
        visit.addEncounter(encounter1);

        assertThat(new VisitDomainWrapper(visit).getOldestEncounter(), is(encounter1));
    }

    @Test
    public void shouldReturnNullOnMostRecentEncounterIfNoEncounters() throws Exception {
        assertThat(new VisitDomainWrapper(new Visit()).getMostRecentEncounter(), is(nullValue()));
    }

    @Test
    public void shouldCloseOnLastEncounterDate() throws Exception {

        Date startDate = new DateTime(2012,2,20,10,10).toDate();
        Date firstEncounterDate = new DateTime(2012,2,24,10,10).toDate();
        Date secondEncounterDate = new DateTime(2012,2,28,10,10).toDate();

        Visit visit = new Visit();
        visit.setStartDatetime(startDate);

        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(secondEncounterDate);

        Encounter anotherEncounter = new Encounter();
        anotherEncounter.setEncounterDatetime(firstEncounterDate);

        visit.addEncounter(encounter);
        visit.addEncounter(anotherEncounter);

        new VisitDomainWrapper(visit).closeOnLastEncounterDatetime();

        assertThat(visit.getStopDatetime(), is(secondEncounterDate));

    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailIfNoEncounters() throws Exception {

        Date startDate = new DateTime(2012,2,20,10,10).toDate();

        Visit visit = new Visit();
        visit.setStartDatetime(startDate);

        new VisitDomainWrapper(visit).closeOnLastEncounterDatetime();
    }

    @Test
    public void shouldReturnDispositionFromObs() throws Exception {

        ConceptService conceptService = mock(ConceptService.class);
        MockMetadataTestUtil.setupMockConceptService(conceptService, emrApiProperties);

        DispositionDescriptor dispositionDescriptor = MockMetadataTestUtil.setupDispositionDescriptor(conceptService);
        when(dispositionService.getDispositionDescriptor()).thenReturn(dispositionDescriptor);

        Encounter mostRecentEncounter = new Encounter();
        mostRecentEncounter.setEncounterDatetime(new DateTime(2012,12,12,12,12).toDate());

        Encounter secondMostRecentEncounter = new Encounter();
        secondMostRecentEncounter.setEncounterDatetime(new DateTime(2012,11,11,11,11).toDate());

        Encounter thirdMostRecentEncounter = new Encounter();
        thirdMostRecentEncounter.setEncounterDatetime(new DateTime(2012, 10, 10, 10,10).toDate());

        Obs mostRecentDispositionGroup = new Obs();
        mostRecentDispositionGroup.setConcept(dispositionDescriptor.getDispositionSetConcept());
        Obs mostRecentOtherObsGroup = new Obs();
        mostRecentOtherObsGroup.setConcept(new Concept());
        mostRecentEncounter.addObs(mostRecentDispositionGroup);
        mostRecentEncounter.addObs(mostRecentOtherObsGroup);

        Obs secondMostRecentDispositionGroup = new Obs();
        secondMostRecentDispositionGroup.setConcept(dispositionDescriptor.getDispositionSetConcept());
        secondMostRecentEncounter.addObs(secondMostRecentDispositionGroup);

        Obs thirdMostRecentDispositionObsGroup = new Obs();
        thirdMostRecentDispositionObsGroup.setConcept(dispositionDescriptor.getDispositionSetConcept());
        thirdMostRecentEncounter.addObs(thirdMostRecentDispositionObsGroup);

        Set<Encounter> encounters = new HashSet<Encounter>();
        encounters.add(secondMostRecentEncounter);
        encounters.add(mostRecentEncounter);
        encounters.add(thirdMostRecentEncounter);
        when(visit.getEncounters()).thenReturn(encounters);

        visitDomainWrapper.getMostRecentDisposition();
        verify(dispositionService).getDispositionFromObsGroup(mostRecentDispositionGroup);
    }

}
