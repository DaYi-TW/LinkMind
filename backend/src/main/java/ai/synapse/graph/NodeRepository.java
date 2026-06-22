package ai.synapse.graph;
import org.springframework.data.mongodb.repository.MongoRepository;
import static ai.synapse.graph.GraphModels.Node;
public interface NodeRepository extends MongoRepository<Node,String>{}
