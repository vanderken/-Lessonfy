# Architecture

## Overview
Lessonfy is a personalized study reader that helps students focus while reading, mark confusing parts, and come back to them later.

## Stack
- Frontend: React
- Storage: Springbot

## Main parts 
User, UserSettings, Lesson, Section, Collection, Pin, PinCollection

## Relationships
- One User has one UserSettings
- One User owns many Lessons
- One User creates many Collections
- One Lesson contains one or many Sections
- One Section holds many Pins
- Pins and Collections are linked through PinCollection (many-to-many)