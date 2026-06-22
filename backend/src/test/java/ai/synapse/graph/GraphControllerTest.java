package ai.synapse.graph;
import ai.synapse.SynapseApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(classes=SynapseApiApplication.class) @AutoConfigureMockMvc class GraphControllerTest {
 @Autowired MockMvc mvc;
 @Test void readsTenSeedNodes() throws Exception {mvc.perform(get("/api/graph")).andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(10)).andExpect(jsonPath("$.nodes[0].id").value("diabetes"));}
 @Test void createsLink() throws Exception {mvc.perform(post("/api/graph/links").contentType(MediaType.APPLICATION_JSON).content("{\"sourceId\":\"hba1c\",\"targetId\":\"exercise\",\"relation\":\"CORRELATES_WITH\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.label").value("CORRELATES_WITH"));}
}
