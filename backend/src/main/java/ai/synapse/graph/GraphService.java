package ai.synapse.graph;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static ai.synapse.graph.GraphModels.*;
@Service public class GraphService {
 private final NodeRepository nodeRepo; private final EdgeRepository edgeRepo;
 public GraphService(NodeRepository nodeRepo,EdgeRepository edgeRepo){this.nodeRepo=nodeRepo;this.edgeRepo=edgeRepo;}
 private static final List<Node> SEED_NODES=List.of(
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
 private static final List<Edge> SEED_EDGES=List.of(
  edge("diabetes","metformin","TREATED_BY"),edge("diabetes","hba1c","MEASURED_BY"),edge("metformin","kidney","CAUTION_WITH"),edge("kidney","lactic","RISK_OF"),edge("metformin","lactic","MAY_CAUSE"),edge("diabetes","insulin","RELATED_TO"),edge("hba1c","guideline","REFERENCED_BY"),edge("guideline","diabetes","DESCRIBES"),edge("insulin","exercise","IMPROVED_BY"),edge("diabetes","hypertension","COMORBID_WITH"),edge("diabetes","sglt2","TREATED_BY"),edge("sglt2","kidney","PROTECTS"),edge("exercise","hypertension","IMPROVES"));
 private static final List<String> ORDER=SEED_NODES.stream().map(Node::id).collect(Collectors.toList());
 @EventListener(ApplicationReadyEvent.class) public void seed(){
  if(nodeRepo.count()==0) nodeRepo.saveAll(SEED_NODES);
  if(edgeRepo.count()==0) edgeRepo.saveAll(SEED_EDGES);
 }

 // --- Reads ---
 public GraphResponse graph(){
  List<Edge> edges=edgeRepo.findAll();
  // Recompute each node's neighbour list from edges so links never drift from the real relations (#8).
  Map<String,LinkedHashSet<String>> neighbours=new HashMap<>();
  for(Edge e:edges){
   neighbours.computeIfAbsent(e.a(),k->new LinkedHashSet<>()).add(e.b());
   neighbours.computeIfAbsent(e.b(),k->new LinkedHashSet<>()).add(e.a());
  }
  List<Node> nodes=nodeRepo.findAll().stream()
   .map(n->new Node(n.id(),n.label(),n.type(),n.x(),n.y(),n.size(),n.description(),n.docs(),
      new ArrayList<>(neighbours.getOrDefault(n.id(),new LinkedHashSet<>()))))
   .sorted(Comparator.comparingInt(n->{int i=ORDER.indexOf(n.id());return i<0?Integer.MAX_VALUE:i;}))
   .collect(Collectors.toList());
  return new GraphResponse(nodes,edges);
 }

 // --- Links ---
 public Edge add(CreateEdgeRequest request){
  if(!nodeRepo.existsById(request.sourceId())||!nodeRepo.existsById(request.targetId())) throw new IllegalArgumentException("Source or target node does not exist");
  if(request.sourceId().equals(request.targetId())) throw new IllegalArgumentException("A node cannot link to itself");
  String relation=normalizeRelation(request.relation());
  // Undirected dedup: a→b and b→a with the same label count as the same relation (#10).
  if(edgeRepo.findAll().stream().anyMatch(e->sameUndirected(e,request.sourceId(),request.targetId())&&e.label().equals(relation)))
   throw new IllegalStateException("Link already exists");
  return edgeRepo.save(edge(request.sourceId(),request.targetId(),relation));
 }
 public void deleteLink(String id){
  if(!edgeRepo.existsById(id)) throw new IllegalArgumentException("Link does not exist");
  edgeRepo.deleteById(id);
 }

 // --- Nodes ---
 public Node addNode(CreateNodeRequest request){
  String id=slug(request.label());
  if(id.isEmpty()) throw new IllegalArgumentException("Label must contain at least one usable character");
  if(nodeRepo.existsById(id)) throw new IllegalStateException("A node with this label already exists");
  String type=request.type().trim();
  if(!Set.of("Disease","Drug","Lab","Document","Concept").contains(type)) throw new IllegalArgumentException("Unknown node type");
  String desc=request.description()==null?"":request.description().trim();
  // New nodes spawn near the centre with a default size; coordinates only seed the 3D layout.
  Node node=new Node(id,request.label().trim(),type,50,50,20,desc,0,List.of());
  return nodeRepo.save(node);
 }
 public void deleteNode(String id){
  if(!nodeRepo.existsById(id)) throw new IllegalArgumentException("Node does not exist");
  // Cascade: drop every edge touching this node (#6).
  edgeRepo.findAll().stream().filter(e->e.a().equals(id)||e.b().equals(id)).forEach(e->edgeRepo.deleteById(e.id()));
  nodeRepo.deleteById(id);
 }

 // --- Ask (keyword graph query, no LLM) ---
 public AskResponse ask(String question){
  String q=question==null?"":question.trim();
  List<Node> nodes=nodeRepo.findAll();
  List<Edge> edges=edgeRepo.findAll();
  if(q.isEmpty()) return new AskResponse("請輸入一個問題，我會在知識圖譜中找出相關的實體與關聯。",List.of(),List.of());
  String lq=q.toLowerCase(Locale.ROOT);
  // Score nodes by how strongly the question references their label/description.
  List<Node> matched=nodes.stream()
   .map(n->Map.entry(n,score(n,lq)))
   .filter(en->en.getValue()>0)
   .sorted((x,y)->Integer.compare(y.getValue(),x.getValue()))
   .limit(5)
   .map(Map.Entry::getKey)
   .collect(Collectors.toList());
  if(matched.isEmpty())
   return new AskResponse("在目前的知識圖譜中找不到與「"+q+"」直接相關的實體。試著用節點名稱（例如：糖尿病、Metformin、腎功能）來提問。",List.of(),List.of());
  Set<String> ids=matched.stream().map(Node::id).collect(Collectors.toCollection(LinkedHashSet::new));
  // Path = edges whose endpoints are both in the matched set (the sub-graph that answers the question).
  List<Edge> path=edges.stream().filter(e->ids.contains(e.a())&&ids.contains(e.b())).collect(Collectors.toList());
  return new AskResponse(buildAnswer(matched,path,nodes),new ArrayList<>(ids),path);
 }

 // --- helpers ---
 private static int score(Node n,String lq){
  int s=0; String label=n.label().toLowerCase(Locale.ROOT);
  if(lq.contains(label)) s+=5;                 // question mentions the node by name
  if(label.contains(lq)) s+=3;
  for(String tok:lq.split("[\\s,，。、？?！!]+")){
   if(tok.length()<2) continue;
   if(label.contains(tok)) s+=2;
   if(n.description()!=null&&n.description().toLowerCase(Locale.ROOT).contains(tok)) s+=1;
  }
  return s;
 }
 private static String buildAnswer(List<Node> matched,List<Edge> path,List<Node> all){
  Map<String,String> labels=all.stream().collect(Collectors.toMap(Node::id,Node::label));
  StringBuilder sb=new StringBuilder();
  String names=matched.stream().map(Node::label).collect(Collectors.joining("、"));
  sb.append("在知識圖譜中找到 ").append(matched.size()).append(" 個相關實體：").append(names).append("。");
  if(path.isEmpty()) sb.append(" 這些實體之間目前沒有直接關聯。");
  else {
   sb.append(" 它們之間的關聯：");
   sb.append(path.stream().limit(4)
     .map(e->labels.getOrDefault(e.a(),e.a())+" —["+e.label()+"]→ "+labels.getOrDefault(e.b(),e.b()))
     .collect(Collectors.joining("；")));
   sb.append("。");
  }
  return sb.toString();
 }
 private static boolean sameUndirected(Edge e,String a,String b){return (e.a().equals(a)&&e.b().equals(b))||(e.a().equals(b)&&e.b().equals(a));}
 private static String normalizeRelation(String r){return r.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]","_");}
 // Build a url-safe id from a label; keep CJK characters (they are valid in Mongo ids and URLs once encoded).
 private static String slug(String label){return label==null?"":label.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+","-").replaceAll("[^\\p{L}\\p{N}_-]","");}
 private static Edge edge(String a,String b,String label){return new Edge(UUID.randomUUID().toString(),a,b,label);}
}
