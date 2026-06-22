package ai.synapse.graph;
import org.springframework.data.mongodb.repository.MongoRepository;
import static ai.synapse.graph.GraphModels.Edge;
public interface EdgeRepository extends MongoRepository<Edge,String>{}
