package com.papertrail.profiler.jaxrs;

import com.papertrail.profiler.CpuProfile;
import org.joda.time.Duration;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.osgi.service.component.annotations.Component;

@Component(
        immediate = true,
        property = {
            "osgi.http.whiteboard.context.path=/",
            "osgi.http.whiteboard.servlet.pattern=/profiler/*"
        },
        service = Servlet.class
)
public class CpuProfileResource extends HttpServlet {

    final Lock lock = new ReentrantLock();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        int duration = 10;
        int frequency = 100;
        if (req.getParameter("duration") != null) {
            duration = Integer.parseInt(req.getParameter("duration"));
        }
        if (req.getParameter("frequency") != null) {
            frequency = Integer.parseInt(req.getParameter("frequency"));
        }
        duration = Math.min(duration, 300);
        frequency = Math.max(1, Math.min(frequency, 1000));
        if (req.getParameter("mode") == null || "profile".equals(req.getParameter("mode"))) {
            resp.setHeader("Content-Disposition", "attachment; filename=profile.pprof");
            resp.setContentType("application/octet-stream");
            try (OutputStream os = resp.getOutputStream()) {
                doProfile(duration, frequency, Thread.State.RUNNABLE, os);
            }
        } else if ("contention".equals(req.getParameter("mode")))  {
            resp.setHeader("Content-Disposition", "attachment; filename=profile.pprof");
            resp.setContentType("application/octet-stream");
            try (OutputStream os = resp.getOutputStream();) {
                doProfile(duration, frequency, Thread.State.BLOCKED, os);
            }
        } else {
            resp.sendError(400, "Unknown mode");
        }
    }

    protected void doProfile(int duration, int frequency, Thread.State state, OutputStream os) throws IOException {
        if (lock.tryLock()) {
            try {
                CpuProfile profile = CpuProfile.record(Duration.standardSeconds(duration), frequency, state);
                if (profile == null) {
                    throw new RuntimeException("could not create CpuProfile");
                }
                profile.writeGoogleProfile(os);
                return;
            } finally {
                lock.unlock();
            }
        }
        throw new RuntimeException("Only one profile request may be active at a time");
    }
}
