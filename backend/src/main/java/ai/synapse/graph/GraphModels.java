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
}
