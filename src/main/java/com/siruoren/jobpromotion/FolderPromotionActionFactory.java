package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Action;
import hudson.model.listeners.ItemListener;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class FolderPromotionActionFactory extends TransientActionFactory<Folder> {

    private static final Logger LOGGER = Logger.getLogger(FolderPromotionActionFactory.class.getName());

    @Override
    @NonNull
    public Class<Folder> type() {
        return Folder.class;
    }

    @Override
    @NonNull
    public Collection<? extends Action> createFor(@NonNull Folder folder) {
        return Collections.singletonList(new FolderPromotionAction(folder));
    }
}
