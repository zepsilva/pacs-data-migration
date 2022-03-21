package pt.ieeta.dicoogle.plugin.datamigration.dicomnetworkops;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.SafeClose;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class MoveSCU extends Device {

    public static enum InformationModel {
        PatientRoot(UID.PatientRootQueryRetrieveInformationModelMove, "STUDY"),
        StudyRoot(UID.StudyRootQueryRetrieveInformationModelMove, "STUDY"),
        PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelMove, "STUDY"),
        CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveMove, "IMAGE"),
        HangingProtocol(UID.HangingProtocolInformationModelMove, null),
        ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelMove, null);

        final String cuid;
        final String level;

        InformationModel(String cuid, String level) {
            this.cuid = cuid;
            this.level = level;
        }
    }

    private static final int[] DEF_IN_FILTER = {
            Tag.SOPInstanceUID,
            Tag.StudyInstanceUID,
            Tag.SeriesInstanceUID
    };

//    private final ApplicationEntity ae = new ApplicationEntity("DICOOGLE-STORAGE");
    private final ApplicationEntity ae;
    private final Connection conn = new Connection();
    private final Connection remote = new Connection();
    private final AAssociateRQ rq = new AAssociateRQ();
    private int priority;
    private String destination;
    private InformationModel model;
    private Attributes keys = new Attributes();
    private int[] inFilter = DEF_IN_FILTER;
    private Association as;
    private int cancelAfter;
    private boolean releaseEager;
    private ScheduledFuture<?> scheduledCancel;

    public MoveSCU(String aeTitle) throws IOException {
        super("movescu");
        ae = new ApplicationEntity(aeTitle);
        addConnection(conn);
        addApplicationEntity(ae);
        ae.addConnection(conn);
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public void setCancelAfter(int cancelAfter) {
        this.cancelAfter = cancelAfter;
    }

    public void setReleaseEager(boolean releaseEager) {
        this.releaseEager = releaseEager;
    }

    public final void setInformationModel(InformationModel model, String[] tss,
                                          boolean relational) {
        this.model = model;
        rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
        if (relational)
            rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[]{1}));
        if (model.level != null)
            addLevel(model.level);
    }

    public void addLevel(String s) {
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
    }

    public final void setDestination(String destination) {
        this.destination = destination;
    }

    public void addKey(int tag, String... ss) {
        VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
        keys.setString(tag, vr, ss);
    }

    public final void setInputFilter(int[] inFilter) {
        this.inFilter  = inFilter;
    }

    public void open() throws IOException, InterruptedException,
            IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(conn, remote, rq);
    }

    public void close() throws IOException, InterruptedException {
        if (scheduledCancel != null && releaseEager) { // release by scheduler thread
            return;
        }
        if (as != null && as.isReadyForDataTransfer()) {
            if (!releaseEager) {
                as.waitForOutstandingRSP();
            }
            as.release();
        }
    }

    public void retrieve(File f, DimseRSPHandler rspHandler) throws IOException, InterruptedException {
        Attributes attrs = new Attributes();
        DicomInputStream dis = null;
        try {
            attrs.addSelected(new DicomInputStream(f).readDataset(), inFilter);
        } finally {
            SafeClose.close(dis);
        }
        attrs.addAll(keys);
        retrieve(attrs, rspHandler);
    }

    public void retrieve(DimseRSPHandler rspHandler) throws IOException, InterruptedException {
        retrieve(keys, rspHandler);
    }

    private void retrieve(Attributes keys, DimseRSPHandler rspHandler) throws IOException, InterruptedException {

        as.cmove(model.cuid, priority, keys, null, destination, rspHandler);
        if (cancelAfter > 0) {
            scheduledCancel = schedule(new Runnable() {
                                           @Override
                                           public void run() {
                                               try {
                                                   rspHandler.cancel(as);
                                                   if (releaseEager) {
                                                       as.release();
                                                   }
                                               } catch (IOException e) {
                                                   e.printStackTrace();
                                               }
                                           }
                                       },
                    cancelAfter,
                    TimeUnit.MILLISECONDS);
        }
    }

    public Connection getRemoteConnection() {
        return remote;
    }

    public AAssociateRQ getAAssociateRQ() {
        return rq;
    }

    public Association getAssociation() {
        return as;
    }

    public Attributes getKeys() {
        return keys;
    }

    public Connection getConn() {
        return conn;
    }
}
