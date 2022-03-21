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
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.servlet.*;

public class QueryTool {

    private static final Logger logger = LoggerFactory.getLogger(QueryTool.class);

    private final FindSCU findSCU;
    private final String callingAET = "DICOOGLE-STORAGE";       // TODO: arranjar o platform proxy para receber o AETitle
    private static String[] IVR_LE_FIRST = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian};   // talvez final ou configuravel, ou retirar do dicoogle platform proxy
    private List<Attributes> responses = new ArrayList<>();
    private int priority = 0;                                   // TODO: talvez fazer string para int enum
    private Attributes queryAtts = new Attributes();
    private Attributes returnKeys = new Attributes();
    private boolean finished;

    public QueryTool(String calledAET, String remoteHostname, int remotePort) throws IOException {
        findSCU = new FindSCU(callingAET);
        findSCU.getAAssociateRQ().setCallingAET(callingAET);
        findSCU.getAAssociateRQ().setCalledAET(calledAET);
        findSCU.getRemoteConnection().setHostname(remoteHostname);
        findSCU.getRemoteConnection().setPort(remotePort);
        finished = false;
    }

    public List<Attributes> query(QueryOption queryOption, int queryTag, String queryVal, String queryLevel, int returnKeyTag) {
        finished = false;
        findSCU.setPriority(priority);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        findSCU.getDevice().setExecutor(executorService);

        EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
        queryOptions.add(queryOption);
        findSCU.setInformationModel(FindSCU.InformationModel.StudyRoot, IVR_LE_FIRST, queryOptions);

        queryAtts.setString(queryTag, ElementDictionary.vrOf(queryTag, null), queryVal);
        returnKeys.setNull(returnKeyTag, ElementDictionary.vrOf(returnKeyTag, null));
        findSCU.getKeys().addAll(queryAtts);
        findSCU.getKeys().addAll(returnKeys);
        findSCU.addLevel(queryLevel);

        try {
            findSCU.open();
            DimseRSPHandler handler = getDimseRSPHandler(findSCU.getAssociation().nextMessageID());
            findSCU.query(handler);
        } catch (InterruptedException | IncompatibleConnectionException | GeneralSecurityException | IOException e) {       // TODO: tratar de exceções
            logger.warn("DICOM C-Find Operation exception: {}", e);
        } finally {
            try {
                findSCU.close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            executorService.shutdown();
        }
        return responses;
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
        if(Status.isPending(status)) {
            logger.info("PENDING");
            responses.add(data);
        } else if(status == 0) {
            finished = true;
        }
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<Attributes> getResponses() {
        return responses;
    }

    public boolean isFinished() {
        return finished;
    }
}
