package com.csit321g1.bairus.lessonfy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "core_usersettings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "bionic_reading", nullable = false)
    private boolean bionicReadingEnabled = true;

    @Column(name = "font_size", nullable = false)
    private short fontSize = 19;

    @Column(name = "line_spacing", nullable = false)
    private double lineSpacing = 1.7;

    @Column(name = "focus_mode", nullable = false)
    private boolean focusModeEnabled = true;

    @Column(name = "theme", nullable = false)
    private String theme = "light";

    @Column(name = "background", nullable = false)
    private String background = "default";

    @Column(name = "sound", nullable = false)
    private String soundType = "off";

    @Column(name = "volume", nullable = false)
    private double soundVolume = 0.4;

    @Column(name = "timer_preset", nullable = false)
    private String timerPreset = "25-5";

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isBionicReadingEnabled() {
        return bionicReadingEnabled;
    }

    public void setBionicReadingEnabled(boolean bionicReadingEnabled) {
        this.bionicReadingEnabled = bionicReadingEnabled;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = (short) fontSize;
    }

    public double getLineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(double lineSpacing) {
        this.lineSpacing = lineSpacing;
    }

    public boolean isFocusModeEnabled() {
        return focusModeEnabled;
    }

    public void setFocusModeEnabled(boolean focusModeEnabled) {
        this.focusModeEnabled = focusModeEnabled;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getSoundType() {
        return soundType;
    }

    public void setSoundType(String soundType) {
        this.soundType = soundType;
    }

    public double getSoundVolume() {
        return soundVolume;
    }

    public void setSoundVolume(double soundVolume) {
        this.soundVolume = soundVolume;
    }

    public String getTimerPreset() {
        return timerPreset;
    }

    public void setTimerPreset(String timerPreset) {
        this.timerPreset = timerPreset;
    }
}
