package pt.ieeta.dicoogle.plugin.datamigration.dicomnetworkops;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MigrationTool extends QueryTool {

    private static final Logger logger = LoggerFactory.getLogger(MigrationTool.class);

    private final MoveSCU moveSCU;
    //private final QueryTool queryTool;
    private final String callingAET = "DICOOGLE-STORAGE";       // TODO: arranjar o platform proxy para receber o AETitle
    private int priority = 0;
    private static String[] IVR_LE_FIRST = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian};   // talvez final ou configuravel, ou retirar do dicoogle platform proxy
    private List<Attributes> responses = new ArrayList<>();

    public MigrationTool(String calledAET, String remoteHostname, int remotePort) throws IOException {
        super(calledAET, remoteHostname, remotePort);
        this.moveSCU = new MoveSCU(callingAET);
        moveSCU.setInstalled(true);
        moveSCU.getAAssociateRQ().setCallingAET(callingAET);
        moveSCU.getAAssociateRQ().setCalledAET(calledAET);
        moveSCU.getRemoteConnection().setHostname(remoteHostname);
        moveSCU.getRemoteConnection().setPort(remotePort);
    }

    public void move(QueryOption queryOption, int queryTag, String queryVal, String queryLevel) {
        query(queryOption, queryTag, queryVal, queryLevel, Tag.StudyInstanceUID);

        moveSCU.setPriority(priority);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        moveSCU.setExecutor(executorService);

        moveSCU.setDestination(callingAET);
        moveSCU.setInformationModel(MoveSCU.InformationModel.StudyRoot, IVR_LE_FIRST, true);
        moveSCU.addLevel(queryLevel);

        while (!isFinished()) {}
        List<Attributes> results = getResponses();

        for(Attributes res : results) {
            Attributes attributes = new Attributes();
            attributes.setString(Tag.StudyInstanceUID, ElementDictionary.vrOf(Tag.StudyInstanceUID, null), res.getString(Tag.StudyInstanceUID));
            moveSCU.getKeys().addAll(attributes);

            try {
                moveSCU.open();
                moveSCU.retrieve(getDimseRSPHandler(moveSCU.getAssociation().nextMessageID()));
            } catch (InterruptedException | IncompatibleConnectionException | GeneralSecurityException | IOException e) {
                logger.warn("DICOM Operation exception: {}", e);
            } finally {
                try {
                    moveSCU.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        executorService.shutdown();
    }

    private DimseRSPHandler getDimseRSPHandler(int messageID) {

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
        }
    }

}
