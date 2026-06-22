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
 @Test void readsTenSeedNodes() throws Exception {mvc.perform(get("/api/graph")).andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(10)).andExpect(jsonPath("$.nodes[0].id").value("knowledge-graph"));}
 @Test void createsLink() throws Exception {mvc.perform(post("/api/graph/links").contentType(MediaType.APPLICATION_JSON).content("{\"sourceId\":\"obsidian\",\"targetId\":\"luhmann\",\"relation\":\"CORRELATES_WITH\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.label").value("CORRELATES_WITH"));}
 @Test void rejectsReversedDuplicateLink() throws Exception {
  // knowledge-graph→second-brain ENABLES is seeded; the reverse with the same label must be rejected as a duplicate (undirected dedup).
  mvc.perform(post("/api/graph/links").contentType(MediaType.APPLICATION_JSON).content("{\"sourceId\":\"second-brain\",\"targetId\":\"knowledge-graph\",\"relation\":\"ENABLES\"}")).andExpect(status().isBadRequest());
 }
 @Test void addsAndDeletesNode() throws Exception {
  mvc.perform(post("/api/graph/nodes").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Roam Research\",\"type\":\"Work\",\"description\":\"連結式筆記工具\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("roam-research"));
  mvc.perform(delete("/api/graph/nodes/roam-research")).andExpect(status().isNoContent());
 }
 @Test void rejectsUnknownNodeType() throws Exception {
  mvc.perform(post("/api/graph/nodes").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Foo\",\"type\":\"Planet\"}")).andExpect(status().isBadRequest());
 }
 @Test void deletesLinkById() throws Exception {
  String body=mvc.perform(post("/api/graph/links").contentType(MediaType.APPLICATION_JSON).content("{\"sourceId\":\"euler\",\"targetId\":\"obsidian\",\"relation\":\"MENTIONED_IN\"}")).andReturn().getResponse().getContentAsString();
  String id=body.replaceAll(".*\"id\":\"([^\"]+)\".*","$1");
  mvc.perform(delete("/api/graph/links/"+id)).andExpect(status().isNoContent());
 }
 @Test void askFindsRelatedEntities() throws Exception {
  mvc.perform(post("/api/graph/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Zettelkasten 是什麼？\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.nodeIds").isArray()).andExpect(jsonPath("$.answer").isNotEmpty());
 }
}
