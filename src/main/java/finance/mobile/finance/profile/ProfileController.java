package finance.mobile.finance.profile;

import finance.mobile.finance.auth.dto.UserEnvelope;
import finance.mobile.finance.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserEnvelope getProfile() {
        return new UserEnvelope(profileService.getProfile());
    }

    @PutMapping
    public UserEnvelope updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return new UserEnvelope(profileService.updateProfile(request));
    }
}
