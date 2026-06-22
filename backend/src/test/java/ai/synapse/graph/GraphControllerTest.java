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
 @Test void rejectsReversedDuplicateLink() throws Exception {
  // diabetes→metformin TREATED_BY is seeded; the reverse with the same label must be rejected as a duplicate (undirected dedup).
  mvc.perform(post("/api/graph/links").contentType(MediaType.APPLICATION_JSON).content("{\"sourceId\":\"metformin\",\"targetId\":\"diabetes\",\"relation\":\"TREATED_BY\"}")).andExpect(status().isBadRequest());
 }
 @Test void addsAndDeletesNode() throws Exception {
  mvc.perform(post("/api/graph/nodes").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Statin\",\"type\":\"Drug\",\"description\":\"降血脂藥物\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("statin"));
  mvc.perform(delete("/api/graph/nodes/statin")).andExpect(status().isNoContent());
 }
 @Test void rejectsUnknownNodeType() throws Exception {
  mvc.perform(post("/api/graph/nodes").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Foo\",\"type\":\"Planet\"}")).andExpect(status().isBadRequest());
 }
 @Test void deletesLinkById() throws Exception {
  String body=mvc.perform(post("/api/graph/links").contentType(MediaType.APPLICATION_JSON).content("{\"sourceId\":\"insulin\",\"targetId\":\"guideline\",\"relation\":\"MENTIONED_IN\"}")).andReturn().getResponse().getContentAsString();
  String id=body.replaceAll(".*\"id\":\"([^\"]+)\".*","$1");
  mvc.perform(delete("/api/graph/links/"+id)).andExpect(status().isNoContent());
 }
 @Test void askFindsRelatedEntities() throws Exception {
  mvc.perform(post("/api/graph/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Metformin 有哪些風險？\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.nodeIds").isArray()).andExpect(jsonPath("$.answer").isNotEmpty());
 }
}
