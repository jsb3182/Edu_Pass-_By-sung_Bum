package kr.go.edu.boundary.attendance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import kr.go.edu.boundary.attendance.AttendanceService.CreateEvent;
import kr.go.edu.boundary.attendance.AttendanceService.Entry;
import kr.go.edu.boundary.attendance.AttendanceService.EventSummary;
import kr.go.edu.boundary.attendance.AttendanceService.Invitation;
import kr.go.edu.boundary.attendance.AttendanceService.PublicEvent;
import kr.go.edu.boundary.attendance.AttendanceService.Receipt;
import kr.go.edu.boundary.attendance.AttendanceService.Submit;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceController {
    private static final String ADMIN = "/api/admin/attendance/events";
    private static final String PUBLIC = "/api/public/attendance";
    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping(ADMIN)
    public List<EventSummary> events() {
        return attendance.events();
    }

    @PostMapping(ADMIN)
    public Invitation create(@Valid @RequestBody CreateEvent request, Authentication auth) {
        return attendance.create(request, auth.getName());
    }

    @PostMapping(ADMIN + "/{id}/renew")
    public Invitation renew(@PathVariable UUID id, Authentication auth) {
        return attendance.renew(id, auth.getName());
    }

    @PostMapping(ADMIN + "/{id}/close")
    public ResponseEntity<Void> close(@PathVariable UUID id, Authentication auth) {
        attendance.close(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(ADMIN + "/{id}/entries")
    public List<Entry> entries(@PathVariable UUID id) {
        return attendance.entries(id);
    }

    @GetMapping(ADMIN + "/{id}/entries/{entryId}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable UUID entryId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(attendance.image(id, entryId));
    }

    @GetMapping(ADMIN + "/{id}/export.xlsx")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        var event = attendance.event(id);
        String disposition = ContentDisposition.attachment()
                .filename("출석부-" + event.eventDate() + ".xlsx", StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .cacheControl(CacheControl.noStore())
                .body(AttendanceWorkbook.create(event, attendance.exportRows(id)));
    }

    @PostMapping(PUBLIC + "/lookup")
    public ResponseEntity<PublicEvent> lookup(@RequestBody Lookup request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attendance.lookup(request.token()));
    }

    @PostMapping(PUBLIC + "/submit")
    public ResponseEntity<Receipt> submit(@Valid @RequestBody Submit request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attendance.submit(request));
    }

    public record Lookup(String token) { }
}
