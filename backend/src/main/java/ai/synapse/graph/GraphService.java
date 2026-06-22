package ai.synapse.graph;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static ai.synapse.graph.GraphModels.*;
@Service public class GraphService {
 private final List<Node> nodes=List.of(
  new Node("diabetes","糖尿病","Disease",50,43,34,"慢性代謝性疾病，與胰島素分泌或作用異常相關。",28,List.of("metformin","hba1c","kidney","insulin")),
  new Node("metformin","Metformin","Drug",68,28,27,"第一線口服降血糖藥物，可降低肝臟葡萄糖生成。",12,List.of("diabetes","kidney","lactic")),
  new Node("hba1c","HbA1c","Lab",31,25,23,"反映近 2–3 個月平均血糖控制狀態的檢驗指標。",17,List.of("diabetes","guideline")),
  new Node("kidney","腎功能","Concept",76,55,22,"影響 Metformin 用藥評估與劑量調整的重要因素。",9,List.of("metformin","lactic")),
  new Node("lactic","乳酸中毒","Disease",66,74,19,"少見但嚴重的不良反應，腎功能不全時風險增加。",6,List.of("metformin","kidney")),
  new Node("insulin","胰島素阻抗","Concept",31,66,23,"第二型糖尿病的重要病理機轉。",21,List.of("diabetes","exercise")),
  new Node("guideline","ADA 2025 指引","Document",14,40,19,"糖尿病照護標準與臨床決策參考文件。",1,List.of("hba1c","diabetes")),
  new Node("exercise","運動介入","Concept",43,81,18,"改善胰島素敏感性與代謝指標的非藥物策略。",14,List.of("insulin","hypertension")),
  new Node("hypertension","高血壓","Disease",20,75,21,"常與糖尿病共病，會增加心血管與腎臟併發症風險。",19,List.of("diabetes","exercise","sglt2")),
  new Node("sglt2","SGLT2 抑制劑","Drug",87,39,23,"具降血糖、心血管與腎臟保護效益的藥物類別。",11,List.of("diabetes","kidney","hypertension")));
 private final List<Edge> edges=new CopyOnWriteArrayList<>(List.of(
  edge("diabetes","metformin","TREATED_BY"),edge("diabetes","hba1c","MEASURED_BY"),edge("metformin","kidney","CAUTION_WITH"),edge("kidney","lactic","RISK_OF"),edge("metformin","lactic","MAY_CAUSE"),edge("diabetes","insulin","RELATED_TO"),edge("hba1c","guideline","REFERENCED_BY"),edge("guideline","diabetes","DESCRIBES"),edge("insulin","exercise","IMPROVED_BY"),edge("diabetes","hypertension","COMORBID_WITH"),edge("diabetes","sglt2","TREATED_BY"),edge("sglt2","kidney","PROTECTS"),edge("exercise","hypertension","IMPROVES")));
 public GraphResponse graph(){return new GraphResponse(nodes,List.copyOf(edges));}
 public Edge add(CreateEdgeRequest request){
  if(nodes.stream().noneMatch(n->n.id().equals(request.sourceId()))||nodes.stream().noneMatch(n->n.id().equals(request.targetId()))) throw new IllegalArgumentException("Source or target node does not exist");
  if(request.sourceId().equals(request.targetId())) throw new IllegalArgumentException("A node cannot link to itself");
  String relation=request.relation().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]","_");
  Edge edge=edge(request.sourceId(),request.targetId(),relation); if(edges.stream().anyMatch(e->e.a().equals(edge.a())&&e.b().equals(edge.b())&&e.label().equals(edge.label()))) throw new IllegalStateException("Link already exists");
  edges.add(edge); return edge;
 }
 private static Edge edge(String a,String b,String label){return new Edge(UUID.randomUUID().toString(),a,b,label);}
}
