package kr.go.edu.boundary.signing;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SigningController {
    private final SigningService signing;

    public SigningController(SigningService signing) {
        this.signing = signing;
    }

    @PostMapping("/api/admin/signing-sessions")
    public SigningService.IssuedSession issue(@RequestBody SigningService.IssueRequest request,
            Authentication authentication) {
        return signing.issue(request, authentication);
    }

    @PostMapping("/api/public/signing-sessions/lookup")
    public ResponseEntity<SigningService.PublicSession> lookup(@RequestBody TokenLookup request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(signing.lookup(request.token()));
    }

    @PostMapping("/api/public/signatures")
    public SigningService.Receipt submit(@RequestBody SigningService.SubmitRequest request) {
        return signing.submit(request);
    }

    @GetMapping("/api/admin/tasks/{id}/signatures")
    public List<SigningService.SignatureSummary> list(@PathVariable UUID id) {
        return signing.list(id);
    }

    @GetMapping("/api/admin/signatures/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore()).body(signing.image(id));
    }

    public record TokenLookup(String token) { }
}
