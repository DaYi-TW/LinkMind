package ai.synapse.graph;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static ai.synapse.graph.GraphModels.*;
@RestController @RequestMapping("/api/graph") public class GraphController {
 private final GraphService service; public GraphController(GraphService service){this.service=service;}
 @GetMapping public GraphResponse graph(){return service.graph();}
 @PostMapping("/links") @ResponseStatus(HttpStatus.CREATED) public Edge add(@Valid @RequestBody CreateEdgeRequest request){return service.add(request);}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST) public ErrorResponse invalid(RuntimeException e){return new ErrorResponse(e.getMessage());}
 public record ErrorResponse(String message){}
}
