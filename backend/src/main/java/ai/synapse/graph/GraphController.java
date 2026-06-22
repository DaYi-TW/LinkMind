package ai.synapse.graph;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static ai.synapse.graph.GraphModels.*;
@RestController @RequestMapping("/api/graph") public class GraphController {
 private final GraphService service; public GraphController(GraphService service){this.service=service;}
 @GetMapping public GraphResponse graph(){return service.graph();}
 @PostMapping("/nodes") @ResponseStatus(HttpStatus.CREATED) public Node addNode(@Valid @RequestBody CreateNodeRequest request){return service.addNode(request);}
 @DeleteMapping("/nodes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteNode(@PathVariable String id){service.deleteNode(id);}
 @PostMapping("/links") @ResponseStatus(HttpStatus.CREATED) public Edge add(@Valid @RequestBody CreateEdgeRequest request){return service.add(request);}
 @DeleteMapping("/links/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteLink(@PathVariable String id){service.deleteLink(id);}
 @PostMapping("/ask") public AskResponse ask(@RequestBody AskRequest request){return service.ask(request==null?null:request.question());}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST) public ErrorResponse invalid(RuntimeException e){return new ErrorResponse(e.getMessage());}
 public record AskRequest(String question){}
 public record ErrorResponse(String message){}
}
