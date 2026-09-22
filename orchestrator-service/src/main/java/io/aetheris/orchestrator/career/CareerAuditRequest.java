package io.aetheris.orchestrator.career;
public record CareerAuditRequest(String targetRole,boolean githubProfileComplete,boolean profileReadme,boolean portfolioLinked,boolean linkedInComplete,int featuredProjects,int documentedProjects,int projectsWithTests,int projectsWithScreenshots,int skillsCount){}
