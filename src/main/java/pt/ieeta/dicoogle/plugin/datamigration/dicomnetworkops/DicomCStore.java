package pt.ieeta.dicoogle.plugin.datamigration.dicomnetworkops;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.UIDDictionary;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.net.*;
import org.dcm4che2.net.service.StorageCommitmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ieeta.dicoogle.plugin.datamigration.DataMigrationPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class DicomCStore extends StorageCommitmentService {

    private static final Logger logger = LoggerFactory.getLogger(DataMigrationPlugin.class);
    private static final String DCM4CHEE_URI_REFERENCED_TS_UID = "1.2.40.0.13.1.1.2.4.94";
    private static final String[] ONLY_IVLE_TS = {UID.ImplicitVRLittleEndian};

    private Device device;
    private NetworkApplicationEntity ae;
    private NetworkConnection conn;

    private Executor executor = new NewThreadExecutor("CSTORE");
    private NetworkApplicationEntity remoteAE = new NetworkApplicationEntity();
    private NetworkApplicationEntity remoteStgcmtAE;
    private NetworkConnection remoteConn = new NetworkConnection();
    private Map<String, Set<String>> as2ts = new HashMap<String, Set<String>>();
    private boolean fileref = false;
    private boolean stgcmt = false;
    private int priority = 0;

    public DicomCStore(String callingAET) {
        remoteAE.setInstalled(true);
        remoteAE.setAssociationAcceptor(true);
        remoteAE.setNetworkConnection(new NetworkConnection[] {remoteConn});
        device = new Device("CSTORE");
        ae = new NetworkApplicationEntity();
        conn = new NetworkConnection();

        device.setNetworkApplicationEntity(ae);
        device.setNetworkConnection(conn);
        ae.setAssociationInitiator(true);
        ae.setAssociationAcceptor(true);
        ae.register(this);
        ae.setNetworkConnection(conn);
        ae.setAETitle(callingAET);
        conn.setHostname("localhost");
        remoteConn.setHostname("localhost");
        remoteConn.setPort(6665);
    }

    public void store(String calledAET, String hostname, int port, List<File> files) throws ConfigurationException, IOException, InterruptedException {
        List<DicomFile> dicomFiles = files.stream().map(DicomFile::new).filter(f -> f.valid).collect(Collectors.toList());
        configureTransferCapability();
        if(dicomFiles.isEmpty()) {
            logger.info("No files sent");
            return;
        }
        remoteAE.setAETitle(calledAET);
        Association association = ae.connect(remoteAE, executor);

        for(DicomFile dicomFile : dicomFiles) {
            TransferCapability tc = association.getTransferCapabilityAsSCU(dicomFile.cuid);

            if (tc == null) {
                logger.warn("{} not supported by {}, skipping file {}", UIDDictionary.getDictionary().prompt(dicomFile.cuid), remoteAE.getAETitle(), dicomFile.file.getName());
                continue;
            }

            try {
                DimseRSPHandler dimseRSPHandler = new DimseRSPHandler();
                association.cstore(dicomFile.cuid, dicomFile.iuid, priority, dicomFile, dicomFile.tsuid, dimseRSPHandler);
            } catch (IOException e) {
                System.out.printf("Failed to send %s to %s - %s%n", dicomFile.file, calledAET, e);
            }
        }
        association.waitForDimseRSP();
        association.release(false);
    }

    public void addTransferCapability(String cuid, String tsuid) {
        Set<String> ts = as2ts.get(cuid);
        if (fileref) {
            if (ts == null) {
                as2ts.put(cuid, Collections.singleton(DCM4CHEE_URI_REFERENCED_TS_UID));
            }
        } else {
            if (ts == null) {
                ts = new HashSet<String>();
                ts.add(UID.ImplicitVRLittleEndian);
                as2ts.put(cuid, ts);
            }
            ts.add(tsuid);
        }
    }

    public final void setStorageCommitment(boolean stgcmt) {
        this.stgcmt = stgcmt;
    }

    public final boolean isStorageCommitment() {
        return stgcmt;
    }

    public void configureTransferCapability() {
        int off = stgcmt || remoteStgcmtAE != null ? 1 : 0;
        TransferCapability[] tc = new TransferCapability[off + as2ts.size()];
        if (off > 0) {
            tc[0] = new TransferCapability(UID.StorageCommitmentPushModelSOPClass, ONLY_IVLE_TS,
                    TransferCapability.SCU);
        }
        Iterator<Map.Entry<String, Set<String>>> iter = as2ts.entrySet().iterator();
        for (int i = off; i < tc.length; i++) {
            Map.Entry<String, Set<String>> e = iter.next();
            String cuid = e.getKey();
            Set<String> ts = e.getValue();
            tc[i] = new TransferCapability(cuid, ts.toArray(new String[ts.size()]), TransferCapability.SCU);
        }
        ae.setTransferCapability(tc);
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public void start() throws IOException {
        if (conn.isListening()) {
            conn.bind(executor);
            logger.info("Start Server listening on port {}", conn.getPort());
        }
    }

    public void stop() {
        if (conn.isListening()) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            conn.unbind();
        }
    }

    /*private void onDimseRSP(DicomObject cmd) {
        int status = cmd.getInt(Tag.Status);
        int msgId = cmd.getInt(Tag.MessageIDBeingRespondedTo);
        FileInfo info = files.get(msgId - 1);
        info.status = status;
        switch (status) {
            case 0:
                info.transferred = true;
                totalSize += info.length;
                ++filesSent;
                System.out.print('.');
                break;
            case 0xB000:
            case 0xB006:
            case 0xB007:
                info.transferred = true;
                totalSize += info.length;
                ++filesSent;
                promptErrRSP("WARNING: Received RSP with Status ", status, info, cmd);
                System.out.print('W');
                break;
            default:
                promptErrRSP("ERROR: Received RSP with Status ", status, info, cmd);
                System.out.print('F');
        }
    }*/

    private class DicomFile implements org.dcm4che2.net.DataWriter {
        final File file;
        String iuid;
        String cuid;
        String tsuid;
        long fmiEnd;
        boolean valid = true;

        DicomFile(File file) {
            this.file = file;
            try (DicomInputStream dis = new DicomInputStream(file)) {
                DicomObject fmi = dis.readFileMetaInformation();
                if (fmi != null) {
                    fmiEnd = dis.getEndOfFileMetaInfoPosition();
                    cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
                    iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
                    tsuid = fmi.getString(Tag.TransferSyntaxUID);
                } else {
                    logger.info("Missing DICOM File Meta Information");
                    valid = false;
                }
            } catch (IOException e) {
                logger.info("Failed to parse {} - {}", file, e);
                valid = false;
            }
            if(valid) {
                addTransferCapability(cuid, tsuid);
            }
        }

        @Override
        public void writeTo(PDVOutputStream out, String tsuid) throws IOException {
            try (FileInputStream in = new FileInputStream(file)) {
                in.skip(fmiEnd);
                out.copyFrom(in);
            }
        }
    }
}
