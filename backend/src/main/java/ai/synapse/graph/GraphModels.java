package ai.synapse.graph;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
public final class GraphModels {
  private GraphModels(){}
  @Document("nodes") public record Node(@Id String id,String label,String type,double x,double y,int size,String description,int docs,List<String> links){}
  @Document("edges") public record Edge(@Id String id,String a,String b,String label){}
  public record GraphResponse(List<Node> nodes,List<Edge> edges){}
  public record CreateEdgeRequest(@NotBlank String sourceId,@NotBlank String targetId,@NotBlank String relation){}
  public record CreateNodeRequest(@NotBlank String label,@NotBlank String type,String description){}
  // A connection as seen from a given node: the neighbour id and the relation label oriented for display.
  public record Connection(String nodeId,String label,boolean outgoing){}
  public record AskResponse(String answer,List<String> nodeIds,List<Edge> path){}
}
