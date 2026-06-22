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
 // Universal node taxonomy for a personal knowledge graph (domain-agnostic).
 private static final Set<String> TYPES=Set.of("Concept","Person","Place","Event","Work","Source","Idea");
 private static final List<Node> SEED_NODES=List.of(
  new Node("knowledge-graph","知識圖譜","Concept",50,46,34,"用節點與關聯來組織知識的方法，是這個工具的核心概念。",12,List.of("second-brain","note-taking","zettelkasten","graph-theory")),
  new Node("second-brain","第二大腦","Idea",70,30,27,"把記憶與思考外部化到數位系統，讓大腦專注於連結與創造。",9,List.of("knowledge-graph","note-taking","tiago-forte")),
  new Node("zettelkasten","Zettelkasten","Concept",30,28,24,"卡片盒筆記法：每則筆記原子化並彼此連結，由 Niklas Luhmann 發揚。",7,List.of("knowledge-graph","note-taking","luhmann")),
  new Node("note-taking","筆記法","Concept",52,68,23,"擷取與組織想法的實踐；連結式筆記是知識圖譜的基礎。",11,List.of("knowledge-graph","second-brain","zettelkasten","obsidian")),
  new Node("obsidian","Obsidian","Work",78,58,21,"以雙向連結與本地 Markdown 著稱的筆記軟體。",5,List.of("note-taking","graph-theory")),
  new Node("graph-theory","圖論","Concept",26,58,22,"研究節點與邊的數學分支，支撐了知識圖譜的結構。",8,List.of("knowledge-graph","obsidian","euler")),
  new Node("luhmann","Niklas Luhmann","Person",16,36,19,"德國社會學家，以 9 萬張卡片的 Zettelkasten 系統聞名。",4,List.of("zettelkasten")),
  new Node("tiago-forte","Tiago Forte","Person",86,24,18,"《Building a Second Brain》作者，提出 CODE 與 PARA 方法。",3,List.of("second-brain")),
  new Node("euler","Leonhard Euler","Person",14,72,18,"奠定圖論的數學家，柯尼斯堡七橋問題的解答者。",4,List.of("graph-theory")),
  new Node("building-second-brain","Building a Second Brain","Source",92,40,19,"Tiago Forte 闡述個人知識管理方法的著作。",2,List.of("tiago-forte","second-brain")));
 private static final List<Edge> SEED_EDGES=List.of(
  edge("knowledge-graph","second-brain","ENABLES"),edge("knowledge-graph","zettelkasten","INSPIRED_BY"),edge("knowledge-graph","note-taking","BUILT_ON"),edge("knowledge-graph","graph-theory","GROUNDED_IN"),edge("second-brain","note-taking","PRACTICED_AS"),edge("second-brain","tiago-forte","COINED_BY"),edge("zettelkasten","note-taking","IS_A"),edge("zettelkasten","luhmann","CREATED_BY"),edge("note-taking","obsidian","TOOL_FOR"),edge("obsidian","graph-theory","VISUALIZES"),edge("graph-theory","euler","FOUNDED_BY"),edge("building-second-brain","tiago-forte","WRITTEN_BY"),edge("building-second-brain","second-brain","DESCRIBES"));
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
  if(!TYPES.contains(type)) throw new IllegalArgumentException("Unknown node type");
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
