package pt.ieeta.dicoogle.plugin.datamigration;

import org.dcm4che2.net.Device;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ieeta.dicoogle.plugin.datamigration.dicomnetworkops.FindSCU;
import pt.ieeta.dicoogle.plugin.datamigration.dicomnetworkops.MigrationTool;
import pt.ieeta.dicoogle.plugin.datamigration.dicomnetworkops.MoveSCU;
import pt.ua.dicoogle.sdk.core.DicooglePlatformInterface;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
*   Dicom Network operations test class
*
* */
public class OperationsTest implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(OperationsTest.class);

    private final String callingAET = "DICOOGLE-STORAGE";
    private final String calledAET = "DICOOGLE-DEST";
    private final Device device = new Device("DICOMOP");
    private final Connection conn = new Connection();
    private final ApplicationEntity ae = new ApplicationEntity("DICOOGLE-STORAGE");
    private static String[] IVR_LE_FIRST = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian};
    private List<Attributes> responses = new ArrayList<>();
    private DicooglePlatformInterface platform;

    public OperationsTest() {}

    @Override
    public void run() {

        MigrationTool migration = null;
        try {
            migration = new MigrationTool(calledAET, "localhost", 1046);
        } catch (IOException e) {
            e.printStackTrace();
        }

        migration.move(QueryOption.RELATIONAL, Tag.Modality, "XA", "STUDY");

        /*device.setInstalled(true);
        ApplicationEntity ae = new ApplicationEntity("DICOOGLE-STORAGE");
        device.addApplicationEntity(ae);
        conn.setHostname("localhost");
        conn.setPort(6666);
        device.addConnection(conn);
        ae.addConnection(conn);*/

////        logger.info("1");
////        String aet = platform.getSettings().getDicomServicesSettings().getAETitle();
////        logger.info("AET: {}", aet);
////        logger.info("2");
//
////        FindSCU findSCU = null;
////        try {
////            findSCU = new FindSCU(callingAET);
////        } catch (IOException e) {
////            e.printStackTrace();
////        }
////        findSCU.setPriority(Priority.NORMAL);
////        findSCU.getAAssociateRQ().setCallingAET(callingAET);
////        findSCU.getAAssociateRQ().setCalledAET(calledAET);
////        findSCU.getRemoteConnection().setHostname("localhost");
////        findSCU.getRemoteConnection().setPort(1046);
//
//        MoveSCU moveSCU = null;
//        try {
//            moveSCU = new MoveSCU(callingAET);
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        moveSCU.setInstalled(true);
//
//        moveSCU.setPriority(Priority.NORMAL);
//        moveSCU.getAAssociateRQ().setCallingAET(callingAET);
//        moveSCU.getAAssociateRQ().setCalledAET(calledAET);
//        moveSCU.getRemoteConnection().setHostname("localhost");
//        moveSCU.getRemoteConnection().setPort(1046);
//
//
//        //ExecutorService executorServiceFind = Executors.newSingleThreadExecutor();
//        ExecutorService executorServiceMove = Executors.newSingleThreadExecutor();
//        //findSCU.getDevice().setExecutor(executorServiceFind);
//
////        EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
////        queryOptions.add(QueryOption.FUZZY);
//        //findSCU.setInformationModel(FindSCU.InformationModel.StudyRoot, IVR_LE_FIRST, queryOptions);
//
//        Attributes attributes = new Attributes();
//        attributes.setString(Tag.StudyInstanceUID, ElementDictionary.vrOf(Tag.StudyInstanceUID, null), "1.2.840.113619.2.21.848.246800003.0.1952805748.3");
//        //attributes.setString(Tag.StudyInstanceUID, ElementDictionary.vrOf(Tag.StudyInstanceUID, null), "1.2.392.200036.9116.4.1.5055.1166");
//
////        Attributes returnKeys = new Attributes();
////        returnKeys.setNull(Tag.StudyInstanceUID, ElementDictionary.vrOf(Tag.StudyInstanceUID, null));
////        findSCU.getKeys().addAll(attributes);
////        findSCU.getKeys().addAll(returnKeys);
//
//        moveSCU.setExecutor(executorServiceMove);
//        moveSCU.getKeys().addAll(attributes);
//        moveSCU.setDestination(callingAET);
//        moveSCU.setInformationModel(MoveSCU.InformationModel.StudyRoot, IVR_LE_FIRST, true);
//        moveSCU.addLevel("STUDY");
//
//        try {
//            moveSCU.open();
//            moveSCU.retrieve(getDimseMoveRSPHandler(moveSCU.getAssociation().nextMessageID()));
////            findSCU.open();
////            DimseRSPHandler handler = getDimseRSPHandler(findSCU.getAssociation().nextMessageID());
////            findSCU.query(handler);
//        } catch (InterruptedException | IncompatibleConnectionException | GeneralSecurityException | IOException e) {
//            logger.warn("DICOM Operation exception: {}", e);
//        } finally {
//            try {
//                //findSCU.close();
//                moveSCU.close();
//            } catch (IOException | InterruptedException e) {
//                e.printStackTrace();
//            }
//            executorServiceMove.shutdown();
//        }
    }

    private DimseRSPHandler getDimseRSPHandler(int messageID) {

        return new DimseRSPHandler(messageID) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd,
                                   Attributes data) {
                super.onDimseRSP(as, cmd, data);
                onCFindResponse(cmd, data);
            }
        };
    }

    protected void onCFindResponse(Attributes cmd, Attributes data) {
        int status = cmd.getInt(Tag.Status, -1);
        logger.info("STATUS: {}", status);
        logger.info("cmd: {}", cmd);
        logger.info("data: {}", data);
        if(Status.isPending(status)) {
            logger.info("PENDING");
            responses.add(data);
        }
    }

    private DimseRSPHandler getDimseMoveRSPHandler(int messageID) {

        return new DimseRSPHandler(messageID) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd,
                                   Attributes data) {
                super.onDimseRSP(as, cmd, data);
                onCMoveResponse(cmd, data);
            }
        };
    }

    protected void onCMoveResponse(Attributes cmd, Attributes data) {
        int status = cmd.getInt(Tag.Status, -1);
        logger.info("STATUS: {}", status);
        logger.info("cmd: {}", cmd);
        logger.info("data: {}", data);
        if(!cmd.contains(Tag. NumberOfRemainingSuboperations)) {
            logger.info("completed: {}", cmd.getInt(Tag.NumberOfCompletedSuboperations,0));
            logger.info("failed: {}", cmd.getInt(Tag.NumberOfFailedSuboperations,0));
            logger.info("warning: {}", cmd.getInt(Tag.NumberOfWarningSuboperations,0));
            logger.info("total: {}", cmd.getInt(Tag.NumberOfCompletedSuboperations,0) + cmd.getInt(Tag.NumberOfFailedSuboperations,0) + cmd.getInt(Tag.NumberOfWarningSuboperations,0));
            /*numFailed = cmd.getInt(Tag.NumberOfFailedSuboperations,0);
            numWarning = cmd.getInt(Tag.NumberOfWarningSuboperations,0);
            numResponses = numSuccess + numFailed + numWarning;*/
        }
    }
}
