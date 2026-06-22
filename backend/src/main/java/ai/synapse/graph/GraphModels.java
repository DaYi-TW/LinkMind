package ai.synapse.graph;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public final class GraphModels {
  private GraphModels(){}
  public record Node(String id,String label,String type,double x,double y,int size,String description,int docs,List<String> links){}
  public record Edge(String id,String a,String b,String label){}
  public record GraphResponse(List<Node> nodes,List<Edge> edges){}
  public record CreateEdgeRequest(@NotBlank String sourceId,@NotBlank String targetId,@NotBlank String relation){}
}
