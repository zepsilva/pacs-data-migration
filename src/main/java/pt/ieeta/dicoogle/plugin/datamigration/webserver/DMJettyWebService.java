package pt.ieeta.dicoogle.plugin.datamigration.webserver;

import net.sf.json.JSONObject;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.QueryOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ieeta.dicoogle.plugin.datamigration.dicomnetworkops.QueryTool;
import pt.ua.dicoogle.sdk.core.DicooglePlatformInterface;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class DMJettyWebService extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DMWebServerPlugin.class);

    private DicooglePlatformInterface platform;

    public DMJettyWebService(DicooglePlatformInterface platform) {
        this.platform = platform;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {       // TODO: atender pedidos de forma assíncrona
//        String test = req.getParameter("string");
//        String aet = platform.getSettings().getDicomServicesSettings().getAETitle();
//        logger.info("aet: {}", aet);

        QueryTool queryTool = new QueryTool("DICOOGLE-DEST", "localhost", 1046);
        queryTool.query(QueryOption.RELATIONAL, Tag.Modality, "XA", "STUDY", Tag.PatientName);

        while(!queryTool.isFinished()) {}       // TODO: usar algum tipo de stream para resultados, com método get bloqueante

        List<Attributes> results = queryTool.getResponses();

        response.setContentType("application/json");
        JSONObject entry = new JSONObject();
        results.forEach(res -> {
            String patientname  = res.getString(Tag.PatientName);
            if(patientname == null)
                patientname = "Unknown";
            entry.put(patientname, res.toString());
        });

        response.getWriter().print(entry);
    }
}
